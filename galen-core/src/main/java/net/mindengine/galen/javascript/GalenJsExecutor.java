/*******************************************************************************
* Copyright 2015 Ivan Shubin http://mindengine.net
* 
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* 
*   http://www.apache.org/licenses/LICENSE-2.0
* 
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
******************************************************************************/
package net.mindengine.galen.javascript;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import net.mindengine.galen.api.Galen;
import net.mindengine.galen.browser.WebDriverWrapper;
import net.mindengine.galen.parser.VarsParserJsProcessable;
import net.mindengine.galen.parser.VarsParserJsProcessor;
import net.mindengine.galen.runner.events.TestEvent;
import net.mindengine.galen.runner.events.TestFilterEvent;
import net.mindengine.galen.runner.events.TestRetryEvent;
import net.mindengine.galen.runner.events.TestSuiteEvent;
import net.mindengine.galen.tests.GalenTest;
import net.mindengine.galen.tests.TestSession;
import net.mindengine.galen.utils.GalenUtils;

import org.apache.commons.io.IOUtils;
import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.ScriptableObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class GalenJsExecutor implements VarsParserJsProcessable {
    private final static Logger LOG = LoggerFactory.getLogger(VarsParserJsProcessor.class);

    private Context context;
    private ImporterTopLevel scope;
    private JsFunctionLoad scriptExecutor;

    public GalenJsExecutor() {
        this.context = Context.enter();
        this.scope = new ImporterTopLevel(context);
        
        this.scriptExecutor = new JsFunctionLoad();
        scope.defineProperty("load", scriptExecutor, ScriptableObject.DONTENUM);
        importAllMajorClasses();
    }

    private void importAllMajorClasses() {
        importClasses(new Class[]{
                Thread.class,
                WebDriverWrapper.class,
                By.class,
                WebElement.class,
                WebDriver.class,
                System.class,
                Actions.class,
                GalenTest.class,
                TestSession.class,
                GalenUtils.class,
                GalenJsApi.class,
                TestEvent.class,
                TestSuiteEvent.class,
                TestFilterEvent.class,
                TestRetryEvent.class,
                Galen.class
        });
    }
    
    private void importClasses(Class<?>[] classes) {
        for (Class<?> clazz : classes) {
            context.evaluateString(scope, "importClass(" + clazz.getName() + ");", "<cmd>", 1, null);
        }
    }


    public void putObject(String name, Object object) {
        ScriptableObject.putProperty(scope, name, Context.javaToJS(object, scope));
    }

    public Object eval(String jsCode) {
        return context.evaluateString(scope, jsCode, "<cmd>", 1, null);
    }

    public Object eval(Reader scriptFileReader, String javascriptPath) throws IOException {
        File file = new File(javascriptPath);
        scriptExecutor.putContextPath(file.getParent());
        return context.evaluateReader(scope, scriptFileReader, javascriptPath, 1, null);
    }


    /**
     * Used for processing js expressions in page spec reader. In case of failure in script returns null
     * @param script - JavaScript code
     * @return result of JavaScript code execution
     */
    @Override
    public String evalSafeToString(String script) {
        try {
            Object returnedObject = context.evaluateString(scope, script, "<cmd>", 1, null);
            if (returnedObject != null) {
                if (returnedObject instanceof Double) {
                    return Integer.toString(((Double) returnedObject).intValue());
                } else if (returnedObject instanceof Float) {
                    return Integer.toString(((Float) returnedObject).intValue());
                } else return returnedObject.toString();
            } else return null;
        }
        catch (Exception ex) {
            LOG.error("Unknown error during processing javascript expressions.", ex);
            return null;
        }
    }

    public static String loadJsFromLibrary(String path) {
        try {
            InputStream is = GalenJsExecutor.class.getResourceAsStream("/js/" + path);
            return  IOUtils.toString(is);
        }
        catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }


    public static String getVersion() {
        return ContextFactory.getGlobal().enterContext().getImplementationVersion();
    }
}