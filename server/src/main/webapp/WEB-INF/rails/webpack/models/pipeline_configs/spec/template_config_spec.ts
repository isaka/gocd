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

import {TemplateConfig} from "models/pipeline_configs/template_config";

describe("TemplateConfig model", () => {

  it("should include a name", () => {
    let pip = new TemplateConfig("name", []);
    expect(pip.isValid()).toBe(true);
    expect(pip.errors().count()).toBe(0);

    pip = new TemplateConfig("", []);
    expect(pip.isValid()).toBe(false);
    expect(pip.errors().count()).toBe(1);
  });

  it("validate name format", () => {
    const pip = new TemplateConfig("my awesome pipeline that has a terrible name", []);
    expect(pip.isValid()).toBe(false);
    expect(pip.errors().count()).toBe(1);
    expect(pip.errors().keys()).toEqual(["name"]);
    expect(pip.errors().errorsForDisplay("name"))
      .toBe(
        "Invalid name. This must be alphanumeric and can contain hyphens, underscores and periods (however, it cannot start with a period). The maximum allowed length is 255 characters.");
  });
});
