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
package org.sonar.application.health;

import java.util.Properties;
import java.util.Random;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.sonar.NetworkUtils;
import org.sonar.api.utils.System2;
import org.sonar.application.cluster.ClusterAppState;
import org.sonar.cluster.ClusterProperties;
import org.sonar.cluster.health.NodeHealth;
import org.sonar.process.ProcessId;
import org.sonar.process.Props;

import static java.lang.String.valueOf;
import static org.apache.commons.lang.RandomStringUtils.randomAlphanumeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.sonar.cluster.ClusterProperties.CLUSTER_NODE_NAME;
import static org.sonar.cluster.ClusterProperties.CLUSTER_NODE_PORT;

public class SearchNodeHealthProviderTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private final Random random = new Random();
  private System2 system2 = mock(System2.class);
  private NetworkUtils networkUtils = mock(NetworkUtils.class);
  private ClusterAppState clusterAppState = mock(ClusterAppState.class);

  @Test
  public void constructor_throws_IAE_if_property_node_name_is_not_set() {
    Props props = new Props(new Properties());

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Missing property: sonar.cluster.node.name");

    new SearchNodeHealthProvider(props, system2, clusterAppState, networkUtils);
  }

  @Test
  public void constructor_throws_NPE_if_NetworkUtils_getHostname_returns_null() {
    Properties properties = new Properties();
    properties.put(ClusterProperties.CLUSTER_NODE_NAME, randomAlphanumeric(3));
    Props props = new Props(properties);

    expectedException.expect(NullPointerException.class);

    new SearchNodeHealthProvider(props, system2, clusterAppState, networkUtils);
  }

  @Test
  public void constructor_throws_IAE_if_property_node_port_is_not_set() {
    Properties properties = new Properties();
    properties.put(ClusterProperties.CLUSTER_NODE_NAME, randomAlphanumeric(3));
    when(networkUtils.getHostname()).thenReturn(randomAlphanumeric(34));
    Props props = new Props(properties);

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Missing property: sonar.cluster.node.port");

    new SearchNodeHealthProvider(props, system2, clusterAppState, networkUtils);
  }

  @Test
  public void constructor_throws_FormatException_if_property_node_port_is_not_an_integer() {
    String port = randomAlphanumeric(3);
    Properties properties = new Properties();
    properties.put(ClusterProperties.CLUSTER_NODE_NAME, randomAlphanumeric(3));
    properties.put(ClusterProperties.CLUSTER_NODE_PORT, port);
    when(networkUtils.getHostname()).thenReturn(randomAlphanumeric(34));
    Props props = new Props(properties);

    expectedException.expect(NumberFormatException.class);
    expectedException.expectMessage("For input string: \"" + port + "\"");

    new SearchNodeHealthProvider(props, system2, clusterAppState, networkUtils);
  }

  @Test
  public void get_returns_name_and_port_from_properties_at_constructor_time() {
    String name = randomAlphanumeric(3);
    int port = 1 + random.nextInt(4);
    Properties properties = new Properties();
    properties.setProperty(CLUSTER_NODE_NAME, name);
    properties.setProperty(CLUSTER_NODE_PORT, valueOf(port));
    when(networkUtils.getHostname()).thenReturn(randomAlphanumeric(34));
    when(system2.now()).thenReturn(1L + random.nextInt(87));
    SearchNodeHealthProvider underTest = new SearchNodeHealthProvider(new Props(properties), system2, clusterAppState, networkUtils);

    NodeHealth nodeHealth = underTest.get();

    assertThat(nodeHealth.getDetails().getName()).isEqualTo(name);
    assertThat(nodeHealth.getDetails().getPort()).isEqualTo(port);

    // change values in properties
    properties.setProperty(CLUSTER_NODE_NAME, randomAlphanumeric(6));
    properties.setProperty(CLUSTER_NODE_PORT, valueOf(1 + random.nextInt(99)));

    NodeHealth newNodeHealth = underTest.get();

    assertThat(newNodeHealth.getDetails().getName()).isEqualTo(name);
    assertThat(newNodeHealth.getDetails().getPort()).isEqualTo(port);
  }

  @Test
  public void get_returns_host_from_NetworkUtils_getHostname_at_constructor_time() {
    String host = randomAlphanumeric(34);
    Properties properties = new Properties();
    properties.setProperty(CLUSTER_NODE_NAME, randomAlphanumeric(3));
    properties.setProperty(CLUSTER_NODE_PORT, valueOf(1 + random.nextInt(4)));
    when(system2.now()).thenReturn(1L + random.nextInt(87));
    when(networkUtils.getHostname()).thenReturn(host);
    SearchNodeHealthProvider underTest = new SearchNodeHealthProvider(new Props(properties), system2, clusterAppState, networkUtils);

    NodeHealth nodeHealth = underTest.get();

    assertThat(nodeHealth.getDetails().getHost()).isEqualTo(host);

    // change now
    when(networkUtils.getHostname()).thenReturn(randomAlphanumeric(96));

    NodeHealth newNodeHealth = underTest.get();

    assertThat(newNodeHealth.getDetails().getHost()).isEqualTo(host);
  }

  @Test
  public void get_returns_started_from_System2_now_at_constructor_time() {
    Properties properties = new Properties();
    long now = setRequiredPropertiesAndMocks(properties);
    SearchNodeHealthProvider underTest = new SearchNodeHealthProvider(new Props(properties), system2, clusterAppState, networkUtils);

    NodeHealth nodeHealth = underTest.get();

    assertThat(nodeHealth.getDetails().getStarted()).isEqualTo(now);

    // change now
    when(system2.now()).thenReturn(now);

    NodeHealth newNodeHealth = underTest.get();

    assertThat(newNodeHealth.getDetails().getStarted()).isEqualTo(now);
  }

  @Test
  public void get_returns_status_GREEN_if_elasticsearch_process_is_operational_in_ClusterAppState() {
    Properties properties = new Properties();
    setRequiredPropertiesAndMocks(properties);
    when(clusterAppState.isOperational(ProcessId.ELASTICSEARCH, true)).thenReturn(true);
    SearchNodeHealthProvider underTest = new SearchNodeHealthProvider(new Props(properties), system2, clusterAppState, networkUtils);

    NodeHealth nodeHealth = underTest.get();

    assertThat(nodeHealth.getStatus()).isEqualTo(NodeHealth.Status.GREEN);
  }

  @Test
  public void get_returns_status_RED_with_cause_if_elasticsearch_process_is_not_operational_in_ClusterAppState() {
    Properties properties = new Properties();
    setRequiredPropertiesAndMocks(properties);
    when(clusterAppState.isOperational(ProcessId.ELASTICSEARCH, true)).thenReturn(false);
    SearchNodeHealthProvider underTest = new SearchNodeHealthProvider(new Props(properties), system2, clusterAppState, networkUtils);

    NodeHealth nodeHealth = underTest.get();

    assertThat(nodeHealth.getStatus()).isEqualTo(NodeHealth.Status.RED);
    assertThat(nodeHealth.getCauses()).containsOnly("Elasticsearch is not operational");
  }

  private long setRequiredPropertiesAndMocks(Properties properties) {
    properties.setProperty(CLUSTER_NODE_NAME, randomAlphanumeric(3));
    properties.setProperty(CLUSTER_NODE_PORT, valueOf(1 + random.nextInt(4)));
    long now = 1L + random.nextInt(87);
    when(system2.now()).thenReturn(now);
    when(networkUtils.getHostname()).thenReturn(randomAlphanumeric(34));
    return now;
  }
}