/**
 *    Copyright 2009-2015 the original author or authors.
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
package org.apache.ibatis.session.defaults;

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.ibatis.exceptions.ExceptionFactory;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.TransactionIsolationLevel;
import org.apache.ibatis.transaction.Transaction;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.managed.ManagedTransactionFactory;

/**
 * 默认的SQLSessionFactory类
 * @author Clinton Begin
 */
public class DefaultSqlSessionFactory implements SqlSessionFactory {
  /**
   * 配置类，设置为final 防止被修改
   */
  private final Configuration configuration;

  public DefaultSqlSessionFactory(Configuration configuration) {
    this.configuration = configuration;
  }

  /**
   * 打开session方法
   * @return
   */
  @Override
  public SqlSession openSession() {
    return openSessionFromDataSource(configuration.getDefaultExecutorType(), null, false);
  }

  @Override
  public SqlSession openSession(boolean autoCommit) {
    return openSessionFromDataSource(configuration.getDefaultExecutorType(), null, autoCommit);
  }

  @Override
  public SqlSession openSession(ExecutorType execType) {
    return openSessionFromDataSource(execType, null, false);
  }

  @Override
  public SqlSession openSession(TransactionIsolationLevel level) {
    return openSessionFromDataSource(configuration.getDefaultExecutorType(), level, false);
  }

  @Override
  public SqlSession openSession(ExecutorType execType, TransactionIsolationLevel level) {
    return openSessionFromDataSource(execType, level, false);
  }

  @Override
  public SqlSession openSession(ExecutorType execType, boolean autoCommit) {
    return openSessionFromDataSource(execType, null, autoCommit);
  }

  @Override
  public SqlSession openSession(Connection connection) {
    return openSessionFromConnection(configuration.getDefaultExecutorType(), connection);
  }

  @Override
  public SqlSession openSession(ExecutorType execType, Connection connection) {
    return openSessionFromConnection(execType, connection);
  }

  @Override
  public Configuration getConfiguration() {
    return configuration;
  }

  private SqlSession openSessionFromDataSource(ExecutorType execType, TransactionIsolationLevel level, boolean autoCommit) {

    Transaction tx = null;
    try {
      //1. 获取配置类
      final Environment environment = configuration.getEnvironment();
      //2.获取事务工厂
      final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
      //3. 创建事务
      tx = transactionFactory.newTransaction(environment.getDataSource(), level, autoCommit);
      //4. 创建执行器
      final Executor executor = configuration.newExecutor(tx, execType);
      //返回默认的sqlSession会话
      return new DefaultSqlSession(configuration, executor, autoCommit);
    } catch (Exception e) {
      //如果异常则关闭事务
      closeTransaction(tx); // may have fetched a connection so lets call close()
      //抛出异常
      throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
    } finally {
      //重新设置错误上下文
      ErrorContext.instance().reset();
    }
  }

  private SqlSession openSessionFromConnection(ExecutorType execType, Connection connection) {
    try {
      boolean autoCommit;
      try {
        //从连接中获取是否提交
        autoCommit = connection.getAutoCommit();
      } catch (SQLException e) {
        //有些驱动或者数据库不支持获取属性，所以异常则设置为true
        // Failover to true, as most poor drivers
        // or databases won't support transactions
        autoCommit = true;
      }
      //1.获取配置
      final Environment environment = configuration.getEnvironment();
      //2.获取事务工厂
      final TransactionFactory transactionFactory = getTransactionFactoryFromEnvironment(environment);
      //创建事务
      final Transaction tx = transactionFactory.newTransaction(connection);
      //获取执行器
      final Executor executor = configuration.newExecutor(tx, execType);
      //创建默认的sql会话
      return new DefaultSqlSession(configuration, executor, autoCommit);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error opening session.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  /**
   * 从环境中获取获取事务工厂
   * @param environment
   * @return
   */
  private TransactionFactory getTransactionFactoryFromEnvironment(Environment environment) {
    //如果没有配置
    if (environment == null || environment.getTransactionFactory() == null) {
      //重新创建
      return new ManagedTransactionFactory();
    }
    //从配置文件获取
    return environment.getTransactionFactory();
  }

  /**
   * 关闭事务控制器
   * @param tx
   */
  private void closeTransaction(Transaction tx) {
    if (tx != null) {
      try {
        tx.close();
      } catch (SQLException ignore) {
        // Intentionally ignore. Prefer previous error.
      }
    }
  }

}
