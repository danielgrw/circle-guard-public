package com.circleguard.auth.support;

import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.lang.Nullable;
import org.springframework.core.io.support.PropertySourceFactory;

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;

/**
 * Enables {@link org.springframework.test.context.TestPropertySource} to load YAML into {@code Environment}.
 */
public class YamlPropertySourceFactory implements PropertySourceFactory {

    @Override
    public PropertySource<?> createPropertySource(@Nullable String name, EncodedResource encodedResource)
            throws IOException {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(encodedResource.getResource());
        factory.afterPropertiesSet();
        Properties properties = factory.getObject();
        Objects.requireNonNull(properties);
        String sourceName = name != null ? name : Objects.requireNonNull(encodedResource.getResource().getFilename());
        return new PropertiesPropertySource(sourceName, properties);
    }
}
