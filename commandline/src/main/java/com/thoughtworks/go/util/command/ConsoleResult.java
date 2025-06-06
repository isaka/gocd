/*
 * Copyright Thoughtworks, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.thoughtworks.go.util.command;

import com.thoughtworks.go.util.ExceptionUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

public class ConsoleResult {
    private final int returnValue;
    private final List<String> output;
    private final List<String> error;
    private final List<CommandArgument> arguments;
    private final List<SecretString> secrets;
    private final boolean failOnNonZeroReturn;

    public ConsoleResult(int returnValue, List<String> output, List<String> error, List<CommandArgument> arguments, List<SecretString> secrets) {
        this(returnValue, output, error, arguments, secrets, true);
    }

    public ConsoleResult(int returnValue, List<String> output, List<String> error, List<CommandArgument> arguments, List<SecretString> secrets, boolean failOnNonZeroReturn) {
        this.returnValue = returnValue;
        this.output = output;
        this.error = new ArrayList<>(error);
        this.arguments = arguments;
        this.secrets = secrets;
        this.failOnNonZeroReturn = failOnNonZeroReturn;
    }

    public List<String> output() {
        return output;
    }

    public List<String> error() {
        return error;
    }

    public String replaceSecretInfo(String line) {
        if (line == null) {
            return null;
        }
        for (CommandArgument argument : arguments) {
            line = argument.replaceSecretInfo(line);
        }
        for (SecretString secret : secrets) {
            line = secret.replaceSecretInfo(line);
        }
        return line;
    }

    public List<String> outputForDisplay() {
        return forDisplay(output);
    }

    public int returnValue() {
        return returnValue;
    }

    public String outputAsString() {
        return String.join("\n", output());
    }

    public String outputForDisplayAsString() {
        return String.join("\n", outputForDisplay());
    }

    public String errorAsString() {
        return String.join("\n", error());
    }

    public String errorForDisplayAsString() {
        return String.join("\n", forDisplay(error));
    }

    private List<String> forDisplay(List<String> from) {
        List<String> forDisplay = new ArrayList<>();
        for (String line : from) {
            forDisplay.add(replaceSecretInfo(line));
        }
        return forDisplay;
    }

    public boolean failed() {
        // Some git commands return non-zero return value for a "successful" command (e.g. git config --get-regexp)
        // In such a scenario, we can't simply rely on return value to tell whether a command is successful or not
        return failOnNonZeroReturn && returnValue() != 0;
    }

    public String describe() {
        return
                "--- EXIT CODE (" + returnValue() + ") ---\n"
                + "--- STANDARD OUT ---\n" + outputForDisplayAsString() + "\n"
                + "--- STANDARD ERR ---\n" + errorForDisplayAsString() + "\n"
                + "---\n";
    }

    public static ConsoleResult unknownResult() {
        return new ConsoleResult(-1, List.of(), List.of("Unknown result."), List.of(), List.of());
    }

    public Exception smudgedException(Exception rawException) {
        try {
            Throwable cause = rawException.getCause();
            if (cause != null) {
                smudgeException(cause);
            }
            smudgeException(rawException);
        } catch (Exception e) {
            ExceptionUtils.bomb(e);
        }
        return rawException;
    }

    private void smudgeException(Throwable rawException) throws NoSuchFieldException, IllegalAccessException {
        Field messageField = Throwable.class.getDeclaredField("detailMessage");
        messageField.setAccessible(true);
        messageField.set(rawException,replaceSecretInfo(rawException.getMessage()));
    }
}
