/*
 * SonarQube
 * Copyright (C) 2009-2017 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.sonarqube.tests.startup;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.util.NetworkUtils;
import java.io.File;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.DisableOnDebug;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.TestRule;
import org.junit.rules.Timeout;
import org.sonarqube.tests.LogsTailer;
import org.sonarqube.tests.Tester;

import static org.assertj.core.api.Assertions.assertThat;
import static util.ItUtils.pluginArtifact;

public class StartupIndexation {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();
  @Rule
  public TestRule safeguard = new DisableOnDebug(Timeout.seconds(600));

  @Test
  public void elasticsearch_error_at_startup_must_shutdown_node() throws Exception {
    try (SonarQube sonarQube = new SonarQube()) {
      sonarQube.lockAllElasticsearchWrites();
      sonarQube.resume();
      sonarQube.waitForShutdown();
      assertThat(sonarQube.isStopped()).isTrue();
    }
  }

  private class SonarQube implements AutoCloseable {
    private final Orchestrator orchestrator;
    private final Tester tester;
    private final File pauseFile;
    private final LogsTailer logsTailer;
    private final LogsTailer.Watch stopWatcher;
    private final int esHttpPort = NetworkUtils.getNextAvailablePort(InetAddress.getLoopbackAddress());

    SonarQube() throws Exception {
      pauseFile = temp.newFile();
      FileUtils.touch(pauseFile);

      orchestrator = Orchestrator.builderEnv()
        .setServerProperty("sonar.web.pause.path", pauseFile.getAbsolutePath())
        .addPlugin(pluginArtifact("wait-at-platform-level4-plugin"))
        .setStartupLogWatcher(l -> l.contains("PlatformLevel4 initialization phase is paused"))
        .setServerProperty("sonar.search.httpPort", "" + esHttpPort)
        .build();
      tester = new Tester(orchestrator);
      tester.setElasticsearchHttpPort(esHttpPort);

      orchestrator.start();
      logsTailer = LogsTailer.builder()
        .addFile(orchestrator.getServer().getCeLogs())
        .addFile(orchestrator.getServer().getAppLogs())
        .build();
      stopWatcher = logsTailer.watch("SonarQube is stopped");
    }

    LogsTailer logs() {
      return logsTailer;
    }

    void resume() throws Exception {
      FileUtils.forceDelete(pauseFile);
    }

    void lockElasticsearchWritesOn(String index) throws Exception {
      tester.elasticsearch().lockWrites(index);
    }

    void lockAllElasticsearchWrites() throws Exception {
      for (String index : Arrays.asList("metadatas", "components", "tests", "projectmeasures", "rules", "issues", "users", "views")) {
        lockElasticsearchWritesOn(index);
      }
    }

    void waitForShutdown() throws InterruptedException {
      stopWatcher.waitForLog(10, TimeUnit.SECONDS);
    }

    boolean isStopped() {
      return stopWatcher.getLog().isPresent();
    }

    @Override
    public void close() throws Exception {
      if (stopWatcher != null) {
        stopWatcher.close();
      }
      if (orchestrator != null) {
        orchestrator.stop();
      }
    }
  }
}
