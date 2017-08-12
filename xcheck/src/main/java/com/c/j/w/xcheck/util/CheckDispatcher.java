package com.c.j.w.xcheck.util;

import com.c.j.w.xcheck.config.context.XAnnotationConfigApplicationContext;
import com.c.j.w.xcheck.support.XBean;
import com.c.j.w.xcheck.support.XCheckHandlerAdapter;
import com.c.j.w.xcheck.support.XResult;
import com.c.j.w.xcheck.support.annotation.Check;
import com.c.j.w.xcheck.util.handler.ValidationHandler;
import com.c.j.w.xcheck.util.item.XCheckItem;
import com.c.j.w.xcheck.support.XBean;
import com.c.j.w.xcheck.support.XCheckHandlerAdapter;
import com.c.j.w.xcheck.support.XResult;
import com.c.j.w.xcheck.support.annotation.Check;
import com.c.j.w.xcheck.config.context.XAnnotationConfigApplicationContext;
import com.c.j.w.xcheck.util.handler.ValidationHandler;
import com.c.j.w.xcheck.util.handler.XFactory;
import com.c.j.w.xcheck.util.item.XCheckItem;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class CheckDispatcher {

    @Autowired(required = false)
    private XCheckHandlerAdapter xCheckHandlerAdapter;
    @Autowired
    private XFactory xFactory;

    /**
     * 校验入口
     * @return
     */
    public XResult check(Method method, HttpServletRequest request) {
        // 判断是否验证对象
        boolean isAnnotationPresent = method.isAnnotationPresent(Check.class);
        // 带有Check注解的方法进行参数规则校验
        if (isAnnotationPresent) {
            Check check = method.getAnnotation(Check.class);
            XBean xBean = XAnnotationConfigApplicationContext.getXBean(check);
            if (xBean == null) {
                throw new RuntimeException("未配置扫描校验对象,请在XCheckContext中配置ControllerPackage路径");
            }
            return dispatch(xBean, request);
        }
        return XResult.success();
    }

    private XResult dispatch(XBean xBean, HttpServletRequest request) {
        // 参数准备
        Map<String, String[]> requestParams = prepareRequestParams(request, xBean);
        List<XCheckItem> checkItems = xBean.getCheckItems();
        // 遍历表达式
        for (XCheckItem checkItem : checkItems) {
            ValidationHandler handler = xFactory.getCheckHandler(checkItem);
            XResult validate = handler.validate(xBean, checkItem, requestParams);
            if (validate.isNotPass()) {
                if (StringUtil.isNotEmpty(checkItem.getMessage())) {
                    validate.setMessage(checkItem.getMessage());
                }
                return validate;
            }
        }
        return XResult.success();
    }

    private Map<String, String[]>  prepareRequestParams(HttpServletRequest request, XBean xBean) {
        Map<String, String[]> parameterMap = request.getParameterMap();
        Map<String, String[]> newParameterMap = new HashMap<>();
        parameterMap.entrySet().stream().forEach(entry -> {
            String k = entry.getKey();
            if (k.contains("[")) {
                newParameterMap.put(k.replaceAll("\\[\\d+\\]", ""), entry.getValue());
            } else {
                newParameterMap.put(k, entry.getValue());
            }
        });

        if (xBean.hasPathParam()) {
            Map<String, String> pathVariableMap = (Map<String, String>)request.getAttribute("org.springframework.web.servlet.HandlerMapping.uriTemplateVariables");
            pathVariableMap.entrySet().stream().forEach(
                    entry -> newParameterMap.put(entry.getKey(), new String[]{entry.getValue()})
            );
        }
        return newParameterMap;
    }

    /**
     * 检查校验响应是否正确配置
     */
    @PostConstruct
    public void checkEnv() {
        if (xCheckHandlerAdapter == null) {
            throw new RuntimeException("XCheckHandlerAdapter unimplemented");
        }
    }
}
