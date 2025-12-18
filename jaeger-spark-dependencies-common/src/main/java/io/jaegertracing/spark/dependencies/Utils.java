/**
 * Copyright 2017 The Jaeger Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package io.jaegertracing.spark.dependencies;

import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;

/**
 * @author Pavol Loffay
 */
public class Utils {
  private Utils() {}

  public static String getEnv(String key, String defaultValue) {
    String result = System.getenv(key);
    return result != null ? result : defaultValue;
  }

  public static void checkNoTNull(String msg, Object object) {
    if (object == null) {
      throw new NullPointerException(String.format("%s is null", msg));
    }
  }

  /**
   * Returns the path to the uber jar containing the calling class.
   * This is used to distribute the jar to Spark workers.
   */
  public static String pathToUberJar(Class<?> clazz) throws UnsupportedEncodingException {
    URL jarFile = clazz.getProtectionDomain().getCodeSource().getLocation();
    return URLDecoder.decode(jarFile.getPath(), "UTF-8");
  }
}
