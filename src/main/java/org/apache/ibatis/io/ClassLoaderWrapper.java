/**
 *    Copyright 2009-2018 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.io;

import java.io.InputStream;
import java.net.URL;

/**
 * A class to wrap access to multiple class loaders making them work as one
 * 类加载包装器，用于类加载访问
 * @author Clinton Begin
 */
public class ClassLoaderWrapper {

  /**
   * 默认类加载器
   */
  ClassLoader defaultClassLoader;
  /**
   * 系统类加载器
   */
  ClassLoader systemClassLoader;

  /**
   * 初始化获取类加载器
   */
  ClassLoaderWrapper() {
    try {
      systemClassLoader = ClassLoader.getSystemClassLoader();
    } catch (SecurityException ignored) {
      // AccessControlException on Google App Engine   
    }
  }

  /**
   * 从classath下获取资源文件
   * @param resource 资源文件名
   * @return 返回URL
   */
  public URL getResourceAsURL(String resource) {
    return getResourceAsURL(resource, getClassLoaders(null));
  }

  /**
   *
   * 用具体的类加载器获取资源文件
   * @param resource    - the resource to find
   * @param classLoader - the first classloader to try
   * @return the stream or null
   */
  public URL getResourceAsURL(String resource, ClassLoader classLoader) {
    return getResourceAsURL(resource, getClassLoaders(classLoader));
  }

  /**
   * 从classath下获取资源文件
   *
   * @param resource - the resource to find
   * @return the stream or null
   */
  public InputStream getResourceAsStream(String resource) {
    return getResourceAsStream(resource, getClassLoaders(null));
  }

  /**
   * 用具体的类加载器获取资源文件
   *
   * @param resource    - the resource to find
   * @param classLoader - the first class loader to try
   * @return the stream or null
   */
  public InputStream getResourceAsStream(String resource, ClassLoader classLoader) {
    return getResourceAsStream(resource, getClassLoaders(classLoader));
  }

  /**
   * 在classpath下根据类名来找类
   *
   * @param name - the class to look for
   * @return - the class
   * @throws ClassNotFoundException Duh.
   */
  public Class<?> classForName(String name) throws ClassNotFoundException {
    return classForName(name, getClassLoaders(null));
  }

  /**
   * 在classpath下根据类名来找类，默认有一个加载器
   *
   * @param name        - the class to look for
   * @param classLoader - the first classloader to try
   * @return - the class
   * @throws ClassNotFoundException Duh.
   */
  public Class<?> classForName(String name, ClassLoader classLoader) throws ClassNotFoundException {
    return classForName(name, getClassLoaders(classLoader));
  }

  /**
   * 尝试通过类加载器加载资源文件，返回一个输入流
   *
   * @param resource    - the resource to get
   * @param classLoader - the classloaders to examine
   * @return the resource or null
   */
  InputStream getResourceAsStream(String resource, ClassLoader[] classLoader) {
    //循环，其实或许用for i会更快一点
    for (ClassLoader cl : classLoader) {
      //判断加载器不为空，由于会出现类加载出现空的情况
      if (null != cl) {

        // 尝试通过加载器进行获取数据
        InputStream returnValue = cl.getResourceAsStream(resource);

        // now, some class loaders want this leading "/", so we'll add it and try again if we didn't find the resource
        if (null == returnValue) {
          //继续加载/下的资源文件，看是否有无数据
          returnValue = cl.getResourceAsStream("/" + resource);
        }
        //不等于null则返回returnValue
        if (null != returnValue) {
          return returnValue;
        }
      }
    }
    return null;
  }

  /**
   * 尝试通过类加载器加载资源文件，返回一个路径
   *
   * @param resource    - the resource to locate
   * @param classLoader - the class loaders to examine
   * @return the resource or null
   */
  URL getResourceAsURL(String resource, ClassLoader[] classLoader) {

    URL url;
    //遍历
    for (ClassLoader cl : classLoader) {
      //判断加载器是否为空
      if (null != cl) {
        //1. 从加载器中加载
        // look for the resource as passed in...
        url = cl.getResource(resource);
        //2. 从/根目录下加载
        // ...but some class loaders want this leading "/", so we'll add it
        // and try again if we didn't find the resource
        if (null == url) {
          url = cl.getResource("/" + resource);
        }

        // "It's always in the last place I look for it!"
        // ... because only an idiot would keep looking for it after finding it, so stop looking already.
        //存在则返回
        if (null != url) {
          return url;
        }

      }

    }

    // didn't find it anywhere.
    //不存在返回空
    return null;

  }

  /**
   * 通过类加载器加载类
   * 不存在则抛出异常，调皮的作者写的异常注释，哈哈哈哈
   * @param name        - the class to load
   * @param classLoader - the group of classloaders to examine
   * @return the class
   * @throws ClassNotFoundException - remember the wisdom of judge smails: well, the world needs ditch diggers, too.调皮的作者写的异常注释，哈哈哈哈
   */
  Class<?> classForName(String name, ClassLoader[] classLoader) throws ClassNotFoundException {
    //同样，循环loader
    for (ClassLoader cl : classLoader) {

      if (null != cl) {

        try {
          //通过loader加载类
          Class<?> c = Class.forName(name, true, cl);

          if (null != c) {
            return c;
          }
        //这里注意，如果没找到则会忽略这个异常，因为不能确定这个类加载器加载了之后，可能其他加载器可以加载该类所以，抛出
        } catch (ClassNotFoundException e) {
          // we'll ignore this until all classloaders fail to locate the class
        }

      }

    }
    //重新抛出
    throw new ClassNotFoundException("Cannot find class: " + name);

  }

  /**
   * 获取类加载器
   * classLoader 外部传递进来的loader
   * defaultClassLoader 默认的loader
   * Thread.currentThread().getContextClassLoader(): 从线程上下文中获取的loader
   * getClass().getClassLoader() 当前类的类加载器
   * systemClassLoader 系统类加载器
   *
   * 优先级: 外部传递->默认->线程->当前类->系统
   * @param classLoader
   * @return
   */
  ClassLoader[] getClassLoaders(ClassLoader classLoader) {
    return new ClassLoader[]{
        classLoader,
        defaultClassLoader,
        Thread.currentThread().getContextClassLoader(),
        getClass().getClassLoader(),
        systemClassLoader};
  }

}
