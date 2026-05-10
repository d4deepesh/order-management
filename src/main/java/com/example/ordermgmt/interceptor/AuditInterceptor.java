package com.example.ordermgmt.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
/**
 * AUDIT INTERCEPTOR
 *
 * CONCEPT: HandlerInterceptor -- runs inside Spring MVC
 * AFTER DispatcherServlet, BEFORE controller method
 *
 * INTERVIEW POINTS:
 *
 * THREE METHODS:
 *
 * preHandle():
 *   - Runs BEFORE controller method
 *   - Has access to HandlerMethod (controller class + method name)
 *   - return true --> continue to Contoller
 *   - return false --> abort, MUST write response yourself
 *   - NOT called if filter aborted the request
 *   - Used for:
 *     authentication checks, logging, request validation, timing start
 *
 * postHandle():
 *   - Runs AFTER CONTROLLER METHODS, before VIEW RENDERING or BEFORE response committed.
 *   - NOT called if exception thrown in controller
 *   - Can modify ModelAndView (for MVC apps with templates)
 *   - For REST APIs, response body already written -- limited use Because response body already prepared.
 *   - Mostly useful for: MVC template apps modifying model. Less useful in REST APIs.
 *
 * afterCompletion():
 *    - Runs AFTER complete request, AFTER response written
 *    - Called ALWAYS -- even if exception was thrown
 *    - receives Exception object (null if no exception)
 *    - Use for guaranteed cleanup: metrics, MDC clear, resource release,  final logging
 *
 * FILTER vs INTERCEPTOR -- KEY DIFFERENCE:
 * Filter cannot access HandlerMethod -- it runs before Spring MVC
 * Interceptor CAN access HandlerMethod -- it runs inside Spring MVC
 * This is why audit logging (needs controller name) uses Interceptor
 *
 * This interceptor is registered in InterceptorConfig.java
 */

@Slf4j
@Component
public class AuditInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) {

        // handler can be HandlerMethod (controller) or
        // ResourceHttpRequestHandler (static files)
        // Always check before casting
        if (handler instanceof HandlerMethod handlerMethod) {
            String controllerName =
                    handlerMethod.getBeanType().getSimpleName();
            String methodName =
                    handlerMethod.getMethod().getName();

            log.debug(">>> INTERCEPTOR preHandle | " +
                            "controller={} | method={} | uri={}",
                    controllerName,
                    methodName,
                    request.getRequestURI());

            // store controller info in request attribute
            // useful for metrics tagging in afterCompletion
            request.setAttribute("controllerName", controllerName);
            request.setAttribute("methodName", methodName);
        }

        // return true to continue, false to abort
        return true;
    }

    @Override
    public void postHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            ModelAndView modelAndView) {

        // postHandle: controller executed successfully (no exception)
        // For REST APIs this is rarely useful -- response body already set
        // More useful in traditional MVC to add common model attributes
        if (handler instanceof HandlerMethod) {
            log.debug(">>> INTERCEPTOR postHandle | uri={}",
                    request.getRequestURI());
        }
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex) {

        // afterCompletion: ALWAYS runs (success or exception)
        // Best place for guaranteed cleanup and metrics

        String controllerName =
                (String) request.getAttribute("controllerName");
        String methodName =
                (String) request.getAttribute("methodName");

        if (ex != null) {
            // exception was thrown (may have been handled by @ExceptionHandler)
            log.error(">>> INTERCEPTOR afterCompletion | " +
                            "controller={} | method={} | " +
                            "status={} | exception={}",
                    controllerName, methodName,
                    response.getStatus(),
                    ex.getMessage());
        } else {
            log.debug(">>> INTERCEPTOR afterCompletion | " +
                            "controller={} | method={} | status={}",
                    controllerName, methodName,
                    response.getStatus());
        }
        // In production: record metrics per endpoint
        // e.g. Micrometer counter/timer tagged with controllerName+methodName
    }
}





