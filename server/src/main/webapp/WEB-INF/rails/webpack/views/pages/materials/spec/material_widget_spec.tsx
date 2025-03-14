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

import {timeFormatter} from "helpers/time_formatter";
import m from "mithril";
import {MaterialModification} from "models/config_repos/types";
import {
  MaterialMessages,
  MaterialWithFingerprint,
  MaterialWithModification,
  PackageMaterialAttributes,
  PluggableScmMaterialAttributes
} from "models/materials/materials";
import {materialMessages} from "models/materials/spec/materials_spec";
import {Origin, OriginType} from "models/origin";
import headerStyles from "views/pages/config_repos/index.scss";
import {TestHelper} from "views/pages/spec/test_helper";
import {MaterialWidget} from "../material_widget";
import {git} from "./materials_widget_spec";

describe('MaterialWidgetSpec', () => {
  const helper           = new TestHelper();
  const showModSpy       = jasmine.createSpy("showModifications");
  const triggerUpdateSpy = jasmine.createSpy("triggerUpdate");
  const onEditSpy        = jasmine.createSpy("onEdit");
  const showUsagesSpy    = jasmine.createSpy("showUsages");
  let material: MaterialWithModification;

  afterEach((done) => helper.unmount(done));
  beforeEach(() => {
    material = new MaterialWithModification(MaterialWithFingerprint.fromJSON(git()), true, false, "2019-12-23T10:15:52Z");
  });

  it('should display the header and action buttons', () => {
    mount();

    expect(helper.byTestId("material-icon")).toBeInDOM();
    expect(helper.byTestId("trigger-update")).toBeInDOM();
    expect(helper.byTestId("show-usages")).toBeInDOM();
    expect(helper.byTestId("show-modifications-material")).toBeInDOM();
  });

  it('should render git material attributes in the panel body', () => {
    mount();

    expect(helper.qa('h3')[1].textContent).toBe("Material Attributes");
    expect(helper.byTestId('edit-material')).not.toBeInDOM();

    const attrsElement = helper.byTestId('material-attributes');

    expect(attrsElement).toBeInDOM();
    expect(helper.qa('li', attrsElement).length).toBe(2);
  });

  it('should render ref with link and edit material icon for plugin', () => {
    material.config
      = new MaterialWithFingerprint("plugin", "fingerprint", new PluggableScmMaterialAttributes(undefined, true, "scm-id", "scm_name"));
    mount();

    expect(helper.qa('h3')[1].textContent).toBe("Material Attributes");
    expect(helper.byTestId('edit-material')).toBeInDOM();

    const attrsElement = helper.byTestId('material-attributes');

    expect(attrsElement).toBeInDOM();
    expect(helper.qa('li', attrsElement).length).toBe(1);
    expect(helper.q('a', helper.byTestId('key-value-value-ref')).textContent).toBe('scm_name');
    expect(helper.q('a', helper.byTestId('key-value-value-ref'))).toHaveAttr('href', '/go/admin/scms#!scm_name');
  });

  it('should render ref with link and edit material icon for package', () => {
    material.config
      = new MaterialWithFingerprint("package", "fingerprint", new PackageMaterialAttributes(undefined, true, "pkg-id", "pkg-name", "pkg-repo-name"));
    mount();

    expect(helper.qa('h3')[1].textContent).toBe("Material Attributes");
    expect(helper.byTestId('edit-material')).toBeInDOM();

    const attrsElement = helper.byTestId('material-attributes');

    expect(attrsElement).toBeInDOM();
    expect(helper.qa('li', attrsElement).length).toBe(1);
    expect(helper.q('a', helper.byTestId('key-value-value-ref')).textContent).toBe('pkg-name');
    expect(helper.q('a', helper.byTestId('key-value-value-ref'))).toHaveAttr('href', '/go/admin/package_repositories#!pkg-repo-name/packages/pkg-name');
  });

  it('should display info message if no modifications are present', () => {
    mount();

    expect(helper.byTestId('flash-message-info')).toBeInDOM();
    expect(helper.textByTestId('flash-message-info')).toBe("This material was never parsed");
  });

  it('should display latest modifications', () => {
    material.modification
      = new MaterialModification("GoCD Test User <devnull@example.com>", null, "b9b4f4b758e91117d70121a365ba0f8e37f89a9d", "Initial commit", "2019-12-23T10:25:52Z");
    mount();

    expect(helper.q('h3').textContent).toBe("Latest Modification Details");
    expect(helper.byTestId('latest-modification-details')).toBeInDOM();

    const attrs = helper.qa('li', helper.byTestId('latest-modification-details'));

    expect(attrs.length).toBe(5);
    expect(attrs[0].textContent).toBe("UsernameGoCD Test User <devnull@example.com>");
    expect(attrs[1].textContent).toBe("Email(Not specified)");
    expect(attrs[2].textContent).toBe("Revisionb9b4f4b758e91117d70121a365ba0f8e37f89a9d");
    expect(attrs[3].textContent).toBe("CommentInitial commit");
    expect(attrs[4].textContent).toBe("Modified Time" + timeFormatter.format(material.modification.modifiedTime));

    expect(helper.q('span span', attrs[4])).toHaveAttr('title', '23 Dec, 2019 at 10:25:52 +00:00 Server Time');
  });

  it('should disable modifications icon when modification is null', () => {
    mount();

    expect(helper.byTestId("show-modifications-material")).toBeInDOM();
    expect(helper.byTestId("show-modifications-material")).toBeDisabled();
    expect(helper.byTestId("show-modifications-material")).toHaveAttr('title', 'No modifications to show');
  });

  it('should send a callback to showModifications method', () => {
    material.modification
      = new MaterialModification("GoCD Test User <devnull@example.com>", null, "b9b4f4b758e91117d70121a365ba0f8e37f89a9d", "Initial commit", "2019-12-23T10:25:52Z");
    mount();

    expect(helper.byTestId("show-modifications-material")).toBeInDOM();
    expect(helper.byTestId("show-modifications-material")).not.toBeDisabled();
    expect(helper.byTestId("show-modifications-material")).toHaveAttr('title', 'Show Modifications');

    helper.clickByTestId("show-modifications-material");

    expect(showModSpy).toHaveBeenCalled();
    expect(showModSpy).toHaveBeenCalledWith(material.config, jasmine.any(MouseEvent));
  });

  it('should send a callback to onEdit method', () => {
    material.config = new MaterialWithFingerprint("package", "fingerprint", new PackageMaterialAttributes(undefined, true, "pkg-id"));

    mount();

    helper.clickByTestId("edit-material");

    expect(onEditSpy).toHaveBeenCalled();
    expect(onEditSpy).toHaveBeenCalledWith(material.config, jasmine.any(MouseEvent));
  });

  it('should not add link if shouldShowPackageOrScmLink is false for scm', () => {
    material.config = new MaterialWithFingerprint("plugin", "fingerprint",
                                                  new PluggableScmMaterialAttributes(undefined, true, "some-id", "scm-name"));
    mount(false);

    expect(helper.qa('h3')[1].textContent).toBe("Material Attributes");

    const attrsElement = helper.byTestId('material-attributes');

    expect(attrsElement).toBeInDOM();
    expect(helper.qa('li', attrsElement).length).toBe(1);
    expect(helper.textByTestId('key-value-value-ref')).toBe("scm-name");
    expect(helper.q('a', helper.byTestId('key-value-value-ref'))).not.toBeInDOM();
  });

  it('should not add link if shouldShowPackageOrScmLink is false for package', () => {
    material.config = new MaterialWithFingerprint("package", "fingerprint",
                                                  new PackageMaterialAttributes(undefined, true, "some-pkg-id", "pkg-name", "pkg-repo-name"));
    mount(false);

    expect(helper.qa('h3')[1].textContent).toBe("Material Attributes");

    const attrsElement = helper.byTestId('material-attributes');

    expect(attrsElement).toBeInDOM();
    expect(helper.qa('li', attrsElement).length).toBe(1);
    expect(helper.textByTestId('key-value-value-ref')).toBe("pkg-name");
    expect(helper.q('a', helper.byTestId('key-value-value-ref'))).not.toBeInDOM();
  });

  it('should display inprogress bar if material update is in progress and disable the trigger button', () => {
    material.materialUpdateInProgress = true;
    mount();

    expect(helper.byTestId("material-update-in-progress")).toBeInDOM();
    expect(helper.byTestId("material-update-in-progress")).toHaveClass(headerStyles.configRepoUpdateInProgress);

    expect(helper.byTestId("trigger-update")).toBeDisabled();
    expect(helper.byTestId("trigger-update")).toHaveAttr('title', `Update in progress since ${timeFormatter.format(material.materialUpdateStartTime)}`);
  });

  it('should disable trigger button when canTriggerValue is false', () => {
    material.canTriggerUpdate = false;
    mount();

    expect(helper.byTestId("trigger-update")).toBeDisabled();
    expect(helper.byTestId("trigger-update")).toHaveAttr('title', 'You do not have permission to trigger an update for this material');
  });

  it('should render the messages', () => {
    material.messages = MaterialMessages.fromJSON(materialMessages());
    mount();

    expect(helper.byTestId('messages')).toBeInDOM();
    expect(helper.byTestId('warnings')).toBeInDOM();
    expect(helper.byTestId('message-0', helper.byTestId('warnings'))).toBeInDOM();
    expect(helper.byTestId('errors')).toBeInDOM();
    expect(helper.byTestId('message-0', helper.byTestId('errors'))).toBeInDOM();
  });

  it('should not show material mdu start time if undefined', () => {
    material.materialUpdateInProgress = true;
    material.materialUpdateStartTime  = undefined;
    mount();

    expect(helper.byTestId("trigger-update")).toBeDisabled();
    expect(helper.byTestId("trigger-update")).toHaveAttr('title', 'Update in progress');
  });

  it('should send a callback to trigger update method', () => {
    mount();

    helper.clickByTestId("trigger-update");

    expect(triggerUpdateSpy).toHaveBeenCalled();
    expect(triggerUpdateSpy).toHaveBeenCalledWith(material, jasmine.any(MouseEvent));
  });

  it('should disable edit button for plugin material if defined in config repo', () => {
    material.config
      = new MaterialWithFingerprint("plugin", "fingerprint", new PluggableScmMaterialAttributes(undefined, true, "scm-id", "scm_name", new Origin(OriginType.ConfigRepo, "config-repo-id")));
    mount();

    expect(helper.byTestId('edit-material')).toBeInDOM();
    expect(helper.byTestId('edit-material')).toBeDisabled();
    expect(helper.byTestId('edit-material')).toHaveAttr('title', "Cannot edit material as it is defined in config repo 'config-repo-id'");
  });

  function mount(shouldShowPackageOrScmLink: boolean = true) {
    helper.mount(() => <MaterialWidget material={material} shouldShowPackageOrScmLink={shouldShowPackageOrScmLink}
                                       triggerUpdate={triggerUpdateSpy} onEdit={onEditSpy} showModifications={showModSpy}
                                       showUsages={showUsagesSpy}/>);
  }
});
