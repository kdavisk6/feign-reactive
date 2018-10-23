package feign;

import feign.Param.Expander;
import feign.codec.Encoder;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.reactivestreams.Publisher;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

/**
 * Request Template Factory that uses an internal Flux Publisher.
 */
public class ReactorRequestTemplateFactory implements ReactiveRequestTemplateFactory {

  private final MethodMetadata metadata;
  private final Set<RequestInterceptor> interceptors;
  private final Encoder encoder;
  private final QueryMapEncoder queryMapEncoder;
  private Map<Integer, Expander> expanders = new LinkedHashMap<>();

  public ReactorRequestTemplateFactory(MethodMetadata metadata,
                                       Set<RequestInterceptor> interceptors,
                                       Encoder encoder, QueryMapEncoder queryMapEncoder) {
    this.metadata = metadata;
    this.interceptors = interceptors;
    this.encoder = encoder;
    this.queryMapEncoder = queryMapEncoder;
    this.cacheExpanders();
  }

  @Override
  public Publisher<RequestTemplate> create(Object[] arguments) {

    /* request templates are not thread safe, as a result, we need to create a new one
     * from the initial template created during Contract parsing. */
    return Flux.just(RequestTemplate.from(this.metadata.template()))
        .map(template -> appendHeaders(arguments[metadata.headerMapIndex()], template))
        .map(template -> appendQueries(arguments[metadata.queryMapIndex()], template))
        .map(template -> resolve(arguments, template))
        .map(this::applyInterceptors)
        .map(template -> {
          try {
            return encode(arguments[metadata.bodyIndex()], template);
          } catch (Throwable throwable) {
            throw Exceptions.propagate(throwable);
          }
        });
  }


  /**
   * Create the variable substitution map to based on the Method arguments.
   *
   * @param arguments to resolve.
   * @return a map of resolved template variables.
   */
  private Map<String, Object> getVariableMapFromArguments(Object[] arguments) {
    Map<String, Object> variables = new LinkedHashMap<>();
    Map<Integer, Collection<String>> parameterArguments = this.metadata.indexToName();
    for (Integer index : parameterArguments.keySet()) {
      final Object parameter = arguments[index];
      final Expander expander = this.expanders.get(index);
      Collection<String> parameterNames = parameterArguments.get(index);
      parameterNames.forEach(name -> {
        if (parameter != null) {
          Object value = parameter;
          if (expander != null) {
            value = expander.expand(value);
          }
          variables.put(name, value);
        }
      });
    }

    return variables;
  }

  /**
   * Resolve all expressions on the Request Template.
   *
   * @param arguments containing the resolved values.
   * @param template to resolve.
   * @return the resolved template.
   */
  private RequestTemplate resolve(Object[] arguments, RequestTemplate template) {
    Map<String, Object> variables = getVariableMapFromArguments(arguments);
    return template.resolve(variables);
  }

  /**
   * Encode the Request Body.
   *
   * @param body of the Request.
   * @param template for the Request.
   * @return the updated Request Template specification.
   */
  private RequestTemplate encode(Object body, RequestTemplate template) {
    if (body != null) {
      /* encode the request body, adding it to the request template */
      encoder.encode(body, metadata.bodyType(), template);
    }
    return template;
  }

  /**
   * Apply all registered Request Interceptors to the template.
   *
   * @param template to apply.
   * @return a RequestTemplate with the interceptors applied.
   */
  private RequestTemplate applyInterceptors(RequestTemplate template) {
    interceptors.forEach(requestInterceptor -> requestInterceptor.apply(template));
    return template;
  }

  /**
   * Append any additional request Headers.
   *
   * @param headers map containing the headers and values to append, can be a {@link Map} or
   * {@literal null}
   * @param template to append the headers to.
   * @return the updated template.
   */
  @SuppressWarnings("unchecked")
  private RequestTemplate appendHeaders(Object headers, RequestTemplate template) {
    if (headers != null) {
      if (Map.class.isAssignableFrom(headers.getClass())) {
        ((Map<String, Object>) headers).forEach((key, object) -> {
          if (Iterable.class.isAssignableFrom(object.getClass())) {
            template.header(key, (Iterable) object);
          } else {
            template.header(key, Collections.singleton(object.toString()));
          }
        });
      }
    }
    return template;
  }

  /**
   * Append any additional Query String parameters by resolving provided object.  If the provided
   * object is a Java Bean or simple object, resolution is delegated to the registered {@link
   * QueryMapEncoder}.  If a {@link Map} is provided, each key-value pair is registered.
   *
   * @param queries to append, can be either a {@link Map}, Java Bean, or {@literal null}
   * @param template to append the query parameters to.
   * @return the resolved template.
   */
  @SuppressWarnings("unchecked")
  private RequestTemplate appendQueries(Object queries, RequestTemplate template) {
    if (queries != null) {
      Map<String, Object> resolved;
      if (Map.class.isAssignableFrom(queries.getClass())) {
        resolved = (Map<String, Object>) queries;
      } else {
        resolved = this.queryMapEncoder.encode(queries);
      }

      resolved.forEach((key, object) -> {
        if (Iterable.class.isAssignableFrom(object.getClass())) {
          template.query(key, (Iterable) object);
        } else {
          template.query(key, object.toString());
        }
      });
    }
    return template;
  }

  private void cacheExpanders() {
    this.expanders = new LinkedHashMap<>(this.metadata.indexToExpander());
    if (!this.metadata.indexToExpanderClass().isEmpty()) {
      this.metadata.indexToExpanderClass().forEach(
          (index, expanderClass) -> {
            try {
              Expander expander = expanderClass.getDeclaredConstructor().newInstance();
              expanders.put(index, expander);
            } catch (Exception ex) {
              /* the expander does not have a default constructor and cannot be created */
              throw new IllegalStateException("Expander " + expanderClass.getName() +
                  " does not have a visible no-argument constructor. Expander cannot be created.",
                  ex);
            }
          });
    }
  }


}
