package com.ds.goroute.config.cache;

import org.springframework.cache.interceptor.KeyGenerator;

import java.lang.reflect.Method;

public class CustomKeyGenerator implements KeyGenerator {

    @Override
    public Object generate(Object target, Method method, Object... params) {
        StringBuilder sb = new StringBuilder();
        sb.append(target.getClass().getSimpleName());
        sb.append(".");
        sb.append(method.getName());

        for (Object param : params) {
            sb.append("_");
            if (param == null) {
                sb.append("null");
            } else {
                sb.append(param.toString());
            }
        }
        return sb.toString();
    }
}
