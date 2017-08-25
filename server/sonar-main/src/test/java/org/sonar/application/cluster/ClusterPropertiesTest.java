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

package org.sonar.application.cluster;

import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.application.config.AppSettings;
import org.sonar.application.config.TestAppSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.cluster.ClusterProperties.CLUSTER_ENABLED;
import static org.sonar.cluster.ClusterProperties.CLUSTER_HOSTS;
import static org.sonar.cluster.ClusterProperties.CLUSTER_NAME;
import static org.sonar.cluster.ClusterProperties.CLUSTER_NODE_HOST;
import static org.sonar.cluster.ClusterProperties.CLUSTER_NODE_PORT;
import static org.sonar.cluster.ClusterProperties.CLUSTER_NODE_TYPE;

public class ClusterPropertiesTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private AppSettings appSettings = new TestAppSettings();

  @Test
  public void test_default_values() throws Exception {
    appSettings.getProps().set(CLUSTER_ENABLED, "true");
    appSettings.getProps().set(CLUSTER_NODE_TYPE, "application");
    ClusterProperties props = new ClusterProperties(appSettings);

    assertThat(props.getNetworkInterfaces())
      .isEqualTo(Collections.emptyList());
    assertThat(props.getPort())
      .isEqualTo(9003);
    assertThat(props.getHosts())
      .isEqualTo(Collections.emptyList());
  }

  @Test
  public void test_port_parameter() {
    appSettings.getProps().set(CLUSTER_ENABLED, "true");
    appSettings.getProps().set(CLUSTER_NAME, "sonarqube");
    appSettings.getProps().set(CLUSTER_NODE_TYPE, "application");

    Stream.of("-50", "0", "65536", "128563").forEach(
      port -> {
        appSettings.getProps().set(CLUSTER_NODE_PORT, port);

        ClusterProperties clusterProperties = new ClusterProperties(appSettings);
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage(
          String.format("Cluster port have been set to %s which is outside the range [1-65535].", port));
        clusterProperties.validate();

      });
  }

  @Test
  public void test_interfaces_parameter() {
    appSettings.getProps().set(CLUSTER_ENABLED, "true");
    appSettings.getProps().set(CLUSTER_NAME, "sonarqube");
    appSettings.getProps().set(CLUSTER_NODE_HOST, "8.8.8.8"); // This IP belongs to Google
    appSettings.getProps().set(CLUSTER_NODE_TYPE, "application");

    ClusterProperties clusterProperties = new ClusterProperties(appSettings);
    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage(
      String.format("Interface %s is not available on this machine.", "8.8.8.8"));
    clusterProperties.validate();
  }

  @Test
  public void validate_does_not_fail_if_cluster_enabled_and_name_specified() {
    appSettings.getProps().set(CLUSTER_ENABLED, "true");
    appSettings.getProps().set(CLUSTER_NAME, "sonarqube");
    appSettings.getProps().set(CLUSTER_NODE_TYPE, "application");

    ClusterProperties clusterProperties = new ClusterProperties(appSettings);
    clusterProperties.validate();
  }

  @Test
  public void test_members() {
    appSettings.getProps().set(CLUSTER_ENABLED, "true");
    appSettings.getProps().set(CLUSTER_NAME, "sonarqube");
    appSettings.getProps().set(CLUSTER_NODE_TYPE, "application");

    assertThat(
      new ClusterProperties(appSettings).getHosts()).isEqualTo(
        Collections.emptyList());

    appSettings.getProps().set(CLUSTER_HOSTS, "192.168.1.1");
    assertThat(
      new ClusterProperties(appSettings).getHosts()).isEqualTo(
        Arrays.asList("192.168.1.1:9003"));

    appSettings.getProps().set(CLUSTER_HOSTS, "192.168.1.2:5501");
    assertThat(
      new ClusterProperties(appSettings).getHosts()).containsExactlyInAnyOrder(
        "192.168.1.2:5501");

    appSettings.getProps().set(CLUSTER_HOSTS, "192.168.1.2:5501,192.168.1.1");
    assertThat(
      new ClusterProperties(appSettings).getHosts()).containsExactlyInAnyOrder(
        "192.168.1.2:5501", "192.168.1.1:9003");
  }
}
