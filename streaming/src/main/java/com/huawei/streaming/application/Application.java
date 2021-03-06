/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.huawei.streaming.application;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Strings;
import com.huawei.streaming.config.StreamingConfig;
import com.huawei.streaming.event.EventTypeMng;
import com.huawei.streaming.event.IEventType;
import com.huawei.streaming.event.TupleEventType;
import com.huawei.streaming.exception.ErrorCode;
import com.huawei.streaming.exception.StreamingException;
import com.huawei.streaming.operator.IRichOperator;
import com.huawei.streaming.operator.functionstream.SplitOp;

/**
 * 应用管理
 * <功能详细描述>
 *
 */
public abstract class Application
{
    private static final Logger LOG = LoggerFactory.getLogger(Application.class);
    
    /**
     * 应用程序名称
     */
    private String appName;
    
    /**
     * 所有Schema集合
     */
    private EventTypeMng streamSchema;
    
    /**
     * 算子集合
     */
    private OperatorMng operatorManager;
    
    /**
     * 系统级别的配置属性
     */
    private StreamingConfig conf;
    
    /**
     * <默认构造函数>
     *
     * @param appName 应用名称、
     * @param config 配置属性
     * @throws StreamingException 流处理异常
     */
    public Application(String appName, StreamingConfig config)
        throws StreamingException
    {
        if (Strings.isNullOrEmpty(appName))
        {
            LOG.error("Application name is null.");
            throw new StreamingException(ErrorCode.UNKNOWN_SERVER_COMMON_ERROR);
        }
        
        if (config == null)
        {
            LOG.error("Configuration is null when build application topology.");
            throw new StreamingException(ErrorCode.UNKNOWN_SERVER_COMMON_ERROR);
        }
        
        this.conf = config;
        this.appName = appName;
        this.streamSchema = new EventTypeMng();
        this.operatorManager = new OperatorMng();
    }
    
    /**
     * 增加数据流描述Schema
     *
     * @param schema 事件类型（schema）
     * @throws StreamingException 流处理异常
     */
    public void addEventSchema(TupleEventType schema)
        throws StreamingException
    {
        if (schema == null)
        {
            LOG.error("Failed to add null to application schemas.");
            throw new StreamingException(ErrorCode.UNKNOWN_SERVER_COMMON_ERROR);
        }
        
        LOG.debug("Add stream schema {}.", schema.getEventTypeName());
        this.streamSchema.addEventType(schema);
    }
    
    /**
     * 添加功能
     *
     * @param operator 功能算子
     */
    public void addFunctionStream(IRichOperator operator)
    {
        operatorManager.addFunctionStreamOperator(operator);
    }
    
    /**
     * 添加输出算子
     *
     * @param output 输出算子
     */
    public void addOutputStream(IRichOperator output)
    {
        operatorManager.addOutputStreamOperator(output);
    }
    
    /**
     * 添加输入算子
     *
     * @param input 输入算子
     */
    public void addInputStream(IRichOperator input)
    {
        operatorManager.addInputStreamOperator(input);
    }
    
    /**
     * 获取流算子的所有schema
     *
     * @return 算子schema管理
     */
    public EventTypeMng getStreamSchema()
    {
        return streamSchema;
    }
    
    /**
     * 通过输出流名称寻找对应算子
     *
     * @param streamName 流名称
     * @return 算子
     * @throws StreamingException 流处理异常
     */
    public IRichOperator getOperatorByOutputStreamName(String streamName)
        throws StreamingException
    {
        if (Strings.isNullOrEmpty(streamName))
        {
            return null;
        }
        
        IRichOperator operator = getOperatorFromInputStreams(streamName);
        if (operator != null)
        {
            return operator;
        }
        
        operator = getOperatorFromFunctionStreams(streamName);
        
        if (operator != null)
        {
            return operator;
        }
        
        return getOperatorFromOutputStreams(streamName);
    }
    
    private IRichOperator getOperatorFromInputStreams(String streamName)
    {
        for (IRichOperator input : this.getInputStreams())
        {
            if (streamName.equals(input.getOutputStream()))
            {
                return input;
            }
        }
        return null;
    }
    
    /**
     * 获取所有的输入流
     *
     * @return 输入流列表
     */
    public List<IRichOperator> getInputStreams()
    {
        return operatorManager.getSourceOps();
    }
    
    private IRichOperator getOperatorFromFunctionStreams(String streamName)
    {
        for (IRichOperator function : this.getFunctionstreams())
        {
            if (function instanceof SplitOp)
            {
                SplitOp splitOp = (SplitOp)function;
                if (splitOp.getOutputSchemaMap().containsKey(streamName))
                {
                    return function;
                }
            }
            else
            {
                if (function.getOutputStream().equals(streamName))
                {
                    return function;
                }
            }
            
        }
        return null;
    }
    
    /**
     * 获取所有的功能流算子
     *
     * @return 功能流算子列表
     */
    public List<IRichOperator> getFunctionstreams()
    {
        return operatorManager.getFunctionOps();
    }
    
    private IRichOperator getOperatorFromOutputStreams(String streamName)
    {
        for (IRichOperator output : this.getOutputStreams())
        {
            if (streamName.equals(output.getOutputStream()))
            {
                return output;
            }
        }
        return null;
    }
    
    /**
     * 获取所有的输出流
     *
     * @return 输出流列表
     */
    public List<IRichOperator> getOutputStreams()
    {
        return operatorManager.getOutputOps();
    }
    
    /**
     * 根据名称获取对应的schema
     *
     * @param name 名称
     * @return schema
     */
    public IEventType getEventType(String name)
    {
        return streamSchema.getEventType(name);
    }
    
    /**
     * 获取已经排好序的功能算子，这个功能算子包含output算子
     *
     * @return 算子列表
     * @throws StreamingException 流处理异常
     */
    public List<IRichOperator> genFunctionOpsOrder()
        throws StreamingException
    {
        return operatorManager.genFunctionOpsOrder();
    }
    
    /**
     * 获取应用程序名称
     *
     * @return 应用程序名称
     */
    public String getAppName()
    {
        return appName;
    }
    
    /**
     * 获取配置属性
     *
     * @return 配置属性
     */
    public StreamingConfig getConf()
    {
        return conf;
    }
    
    /**
     * 应用远程提交
     *
     * @throws StreamingException 流处理异常
     */
    public abstract void launch()
        throws StreamingException;
    
    /**
     * 查询应用程序
     *
     * @return 如果存在，返回true
     * @throws StreamingException 任务查询异常
     */
    public abstract ApplicationResults getApplications()
        throws StreamingException;
    
    /**
     * 检查任务是否存在
     *
     * @return 如果存在，返回true
     * @throws StreamingException 任务删除异常
     */
    public abstract boolean isApplicationExists()
        throws StreamingException;
    
    /**
     * 删除远程应用
     *
     * @throws StreamingException 任务删除异常
     */
    public abstract void killApplication()
        throws StreamingException;
    
    /**
     * 设置用户已经打包好的Jar包
     *
     * @param userJar 用户jar包
     */
    public abstract void setUserPackagedJar(String userJar);
}
