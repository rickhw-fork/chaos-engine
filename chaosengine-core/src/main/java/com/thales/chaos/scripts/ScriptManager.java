package com.thales.chaos.scripts;

import com.thales.chaos.exception.ChaosException;
import com.thales.chaos.scripts.impl.ShellScript;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static com.thales.chaos.exception.enums.ChaosErrorCode.SHELL_SCRIPT_LOOKUP_FAILURE;
import static java.util.function.Predicate.not;

@Component
public class ScriptManager {
    private static final String SCRIPT_SEARCH_PATTERN = "classpath*:ssh/experiments/*";
    private final Collection<Script> scripts = new ArrayList<>();
    private final boolean allowScriptsFromFilesystem;

    public ScriptManager (@Value("${allowScriptsFromFilesystem:false}") boolean allowScriptsFromFilesystem) {
        this.allowScriptsFromFilesystem = allowScriptsFromFilesystem;
    }

    @PostConstruct
    void populateScriptsFromResources () {
        scripts.addAll(Arrays.stream(getResources()).filter(not(Resource::isFile).or(this::allowScriptsFromFilesystem))
                             .map(ShellScript::fromResource)
                             .collect(Collectors.toList()));
    }

    private Resource[] getResources () {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            return resolver.getResources(SCRIPT_SEARCH_PATTERN);
        } catch (IOException e) {
            throw new ChaosException(SHELL_SCRIPT_LOOKUP_FAILURE, e);
        }
    }

    private boolean allowScriptsFromFilesystem (Resource ignored) {
        return allowScriptsFromFilesystem;
    }

    public Collection<Script> getScripts () {
        return getScripts(script -> true);
    }

    public Collection<Script> getScripts (Predicate<? super Script> predicate) {
        return scripts.stream().filter(predicate).collect(Collectors.toList());
    }
}
