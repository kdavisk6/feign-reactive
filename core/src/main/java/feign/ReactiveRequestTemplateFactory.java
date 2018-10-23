package feign;

import org.reactivestreams.Publisher;

public interface ReactiveRequestTemplateFactory {

  Publisher<RequestTemplate> create(Object[] arguments);
}
