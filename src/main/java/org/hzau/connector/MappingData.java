/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.hzau.connector;

import jakarta.servlet.http.MappingMatch;
import org.hzau.Config;
import org.hzau.engine.NormalContext;

import java.util.List;


/**
 * Mapping data.
 *
 * @author Remy Maucherat
 */
public class MappingData {

    //    public Host host = null;
    public NormalContext context = null;
    public Config.Server.Context info=null;
    public int contextSlashCount = 0;
    public List<NormalContext> contexts = null;
    //    public Wrapper wrapper = null;

    public String requestPath = null;
    public String wrapperPath = null;
    public String pathInfo = null;

//    public String redirectPath = null;

    // Fields used by ApplicationMapping to implement jakarta.servlet.http.HttpServletMapping
    public MappingMatch matchType = null;

    public void recycle() {
//        host = null;
        context = null;
        contextSlashCount = 0;
        contexts = null;
//        wrapper = null;
        requestPath = null;
        wrapperPath = null;
        pathInfo = null;
//        redirectPath = null;
        matchType = null;
    }
}
