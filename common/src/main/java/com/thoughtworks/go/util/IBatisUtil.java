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
package com.thoughtworks.go.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Helpers for using ibatis
 */
public class IBatisUtil {
    public static IBatisArgument arguments(String key, Object value) {
        return new IBatisArgument(key, value);
    }

    public static class IBatisArgument {
        private final Map<String, Object> map = new HashMap<>();

        private IBatisArgument(String key, Object value) {
            map.put(key, value);
        }

        public IBatisArgument and(String key, Object value) {
            map.put(key, value);
            return this;
        }

        public Map<String, Object> asMap() {
            return map;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            IBatisArgument that = (IBatisArgument) o;

            return map.equals(that.map);
        }

        @Override
        public int hashCode() {
            return map.hashCode();
        }
    }
}
