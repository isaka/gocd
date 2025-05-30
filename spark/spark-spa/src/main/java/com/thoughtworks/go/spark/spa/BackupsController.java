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
package com.thoughtworks.go.spark.spa;

import com.thoughtworks.go.server.service.BackupService;
import com.thoughtworks.go.spark.Routes;
import com.thoughtworks.go.spark.SparkController;
import com.thoughtworks.go.spark.spring.SPAAuthenticationHelper;
import spark.ModelAndView;
import spark.Request;
import spark.Response;
import spark.TemplateEngine;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static spark.Spark.*;

public class BackupsController implements SparkController {

    private final SPAAuthenticationHelper authenticationHelper;
    private final TemplateEngine engine;
    private final BackupService backupService;

    public BackupsController(SPAAuthenticationHelper authenticationHelper, TemplateEngine engine, BackupService backupService) {
        this.authenticationHelper = authenticationHelper;
        this.engine = engine;
        this.backupService = backupService;
    }

    @Override
    public String controllerBasePath() {
        return Routes.BackupsSPA.BASE;
    }

    @Override
    public void setupRoutes() {
        path(controllerBasePath(), () -> {
            before("", authenticationHelper::checkAdminUserOrGroupAdminUserAnd403);
            get("", this::index, engine);
        });
    }

    public ModelAndView index(Request request, Response response) {
        Map<String, Object> object = Map.of(
            "viewTitle", "Backup",
            "meta", meta()
        );

        return new ModelAndView(object, null);
    }

    private Map<String, String> meta() {
        Map<String, String> meta = new HashMap<>();
        Optional<ZonedDateTime> dateTime = backupService.lastBackupTime().map(d -> ZonedDateTime.ofInstant(Instant.ofEpochMilli(d.getTime()), ZoneId.systemDefault()));
        meta.put("lastBackupTime", dateTime.map(Object::toString).orElse(null));
        meta.put("lastBackupUser", backupService.lastBackupUser().orElse(null));
        meta.put("availableDiskSpace", backupService.availableDiskSpace());
        meta.put("backupLocation", backupService.backupLocation());
        return meta;
    }
}
