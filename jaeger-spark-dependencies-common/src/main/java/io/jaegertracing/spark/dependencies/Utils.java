/**
 * Copyright (c) The Jaeger Authors
 * SPDX-License-Identifier: Apache-2.0
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
