/**
 * Copyright 2018 The Feign Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package feign.reactor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.junit.WireMockClassRule;
import feign.RequestLine;
import feign.reactor.testcase.IcecreamServiceApi;
import feign.reactor.testcase.domain.IceCreamOrder;
import feign.reactor.testcase.domain.OrderGenerator;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static feign.reactor.TestUtils.equalsComparingFieldByFieldRecursively;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Sergii Karpenko
 */
abstract public class DefaultMethodTest {

  @ClassRule
  public static WireMockClassRule wireMockRule = new WireMockClassRule(
      wireMockConfig().dynamicPort());

  @Before
  public void resetServers() {
    wireMockRule.resetAll();
  }

  abstract protected ReactiveFeign.Builder<IcecreamServiceApi> builder();

  abstract protected <API> ReactiveFeign.Builder<API> builder(Class<API> apiClass);

  abstract protected ReactiveFeign.Builder<IcecreamServiceApi> builder(ReactiveOptions options);

  @Test
  public void shouldProcessDefaultMethodOnProxy() throws JsonProcessingException {
    IceCreamOrder orderGenerated = new OrderGenerator().generate(1);
    String orderStr = TestUtils.MAPPER.writeValueAsString(orderGenerated);

    wireMockRule.stubFor(get(urlEqualTo("/icecream/orders/1"))
        .withHeader("Accept", equalTo("application/json"))
        .willReturn(aResponse().withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody(orderStr)));

    IcecreamServiceApi client = builder()
        .target(IcecreamServiceApi.class, "http://localhost:" + wireMockRule.port());

    StepVerifier.create(client.findFirstOrder())
        .expectNextMatches(equalsComparingFieldByFieldRecursively(orderGenerated))
        .verifyComplete();
  }

  @Test
  public void shouldWrapExceptionWithMono() {
    IceCreamOrder orderGenerated = new OrderGenerator().generate(1);

    IcecreamServiceApi client = builder()
        .target(IcecreamServiceApi.class, "http://localhost:" + wireMockRule.port());

    Mono<IceCreamOrder> errorOrder = client.throwExceptionMono().onErrorReturn(
        throwable -> throwable.equals(IcecreamServiceApi.RUNTIME_EXCEPTION),
        orderGenerated);

    StepVerifier.create(errorOrder)
        .expectNextMatches(equalsComparingFieldByFieldRecursively(orderGenerated))
        .verifyComplete();
  }

  @Test
  public void shouldWrapExceptionWithFlux() {
    IceCreamOrder orderGenerated = new OrderGenerator().generate(1);

    IcecreamServiceApi client = builder()
        .target(IcecreamServiceApi.class, "http://localhost:" + wireMockRule.port());

    Flux<IceCreamOrder> errorOrder = client.throwExceptionFlux().onErrorReturn(
        throwable -> throwable.equals(IcecreamServiceApi.RUNTIME_EXCEPTION),
        orderGenerated);

    StepVerifier.create(errorOrder)
        .expectNextMatches(equalsComparingFieldByFieldRecursively(orderGenerated))
        .verifyComplete();
  }

  @Test
  public void shouldOverrideEquals() {

    IcecreamServiceApi client = builder(
        new ReactiveOptions.Builder()
            .setConnectTimeoutMillis(300)
            .setReadTimeoutMillis(100).build())
                .target(IcecreamServiceApi.class,
                    "http://localhost:" + wireMockRule.port());

    IcecreamServiceApi clientWithSameTarget = builder()
        .target(IcecreamServiceApi.class, "http://localhost:" + wireMockRule.port());
    assertThat(client).isEqualTo(clientWithSameTarget);

    IcecreamServiceApi clientWithOtherPort = builder()
        .target(IcecreamServiceApi.class, "http://localhost:" + (wireMockRule.port() + 1));
    assertThat(client).isNotEqualTo(clientWithOtherPort);

    OtherApi clientWithOtherInterface = builder(OtherApi.class)
        .target(OtherApi.class, "http://localhost:" + wireMockRule.port());
    assertThat(client).isNotEqualTo(clientWithOtherInterface);
  }

  interface OtherApi {
    @RequestLine("GET /icecream/flavors")
    Mono<String> method(String arg);
  }

  @Test
  public void shouldOverrideHashcode() {

    IcecreamServiceApi client = builder()
        .target(IcecreamServiceApi.class, "http://localhost:" + wireMockRule.port());

    IcecreamServiceApi otherClientWithSameTarget = builder()
        .target(IcecreamServiceApi.class, "http://localhost:" + wireMockRule.port());

    assertThat(client.hashCode()).isEqualTo(otherClientWithSameTarget.hashCode());
  }

  @Test
  public void shouldOverrideToString() {

    IcecreamServiceApi client = builder()
        .target(IcecreamServiceApi.class, "http://localhost:" + wireMockRule.port());

    assertThat(client.toString())
        .isEqualTo("HardCodedTarget(type=IcecreamServiceApi, "
            + "url=http://localhost:" + wireMockRule.port() + ")");
  }

}
