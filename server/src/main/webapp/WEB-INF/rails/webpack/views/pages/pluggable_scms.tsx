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

import {ErrorResponse} from "helpers/api_request_builder";
import _ from "lodash";
import m from "mithril";
import Stream from "mithril/stream";
import {PluginMetadata, Scm, Scms} from "models/materials/pluggable_scm";
import {PluggableScmCRUD} from "models/materials/pluggable_scm_crud";
import {Origin, OriginType} from "models/origin";
import {Configurations} from "models/shared/configuration";
import {ExtensionTypeString, SCMExtensionType} from "models/shared/plugin_infos_new/extension_type";
import {PluginInfoCRUD} from "models/shared/plugin_infos_new/plugin_info_crud";
import {AnchorVM, ScrollManager} from "views/components/anchor/anchor";
import {ButtonIcon, Primary} from "views/components/buttons";
import {FlashMessage, MessageType} from "views/components/flash_message";
import {SearchField} from "views/components/forms/input_fields";
import {HeaderPanel} from "views/components/header_panel";
import {NoPluginsOfTypeInstalled} from "views/components/no_plugins_installed";
import configRepoStyles from "views/pages/config_repos/index.scss";
import {Page, PageState} from "views/pages/page";
import {PluggableSCMScrollOptions, PluggableScmsWidget} from "views/pages/pluggable_scms/pluggable_scms_widget";
import {UsagePackageModal} from "./package_repositories/package_modals";
import {
  AddOperation,
  CloneOperation,
  DeleteOperation,
  EditOperation,
  RequiresPluginInfos,
  SaveOperation
} from "./page_operations";
import {
  ClonePluggableScmModal,
  CreatePluggableScmModal,
  DeletePluggableScmModal,
  EditPluggableScmModal
} from "./pluggable_scms/modals";

const sm: ScrollManager = new AnchorVM();

interface State extends RequiresPluginInfos, AddOperation<Scm>, EditOperation<Scm>, CloneOperation<Scm>, DeleteOperation<Scm>, SaveOperation {
  scms: Stream<Scms>;
  showUsages: (scm: Scm, e: MouseEvent) => void;
  searchText: Stream<string>;
}

export class PluggableScmsPage extends Page<null, State> {
  private operation: string = "";

  oninit(vnode: m.Vnode<null, State>) {
    super.oninit(vnode);
    vnode.state.scms        = Stream();
    vnode.state.pluginInfos = Stream();
    vnode.state.searchText  = Stream();

    vnode.state.onSuccessfulSave = (msg: m.Children) => {
      this.flashMessage.setMessage(MessageType.success, msg);
      this.fetchData(vnode);
    };

    vnode.state.onError = (msg: m.Children) => {
      this.flashMessage.setMessage(MessageType.alert, msg);
    };

    const onOperationError = (errorResponse: ErrorResponse) => {
      vnode.state.onError(JSON.parse(errorResponse.body!).message);
    };

    vnode.state.onAdd = (e: MouseEvent) => {
      e.stopPropagation();

      const pluginId = vnode.state.pluginInfos()[0].id;
      const scm      = new Scm("", "", true, new Origin(OriginType.GoCD), new PluginMetadata(pluginId, "1"), new Configurations([]));

      new CreatePluggableScmModal(scm, vnode.state.pluginInfos(), vnode.state.onSuccessfulSave)
        .render();
    };

    vnode.state.onEdit = (scm: Scm, e: MouseEvent) => {
      e.stopPropagation();

      new EditPluggableScmModal(scm, vnode.state.pluginInfos(), vnode.state.onSuccessfulSave)
        .render();
    };

    vnode.state.onClone = (scm: Scm, e: MouseEvent) => {
      e.stopPropagation();
      new ClonePluggableScmModal(scm, vnode.state.pluginInfos(), vnode.state.onSuccessfulSave)
        .render();
    };

    vnode.state.onDelete = (scm: Scm, e: MouseEvent) => {
      e.stopPropagation();

      new DeletePluggableScmModal(scm, vnode.state.onSuccessfulSave).render();
    };

    vnode.state.showUsages = (scm: Scm, e: MouseEvent) => {
      e.stopPropagation();

      PluggableScmCRUD.usages(scm.name())
                      .then((result) => {
                        result.do(
                          (successResponse) => {
                            new UsagePackageModal(scm.name(), successResponse.body).render();
                          }, onOperationError);
                      });
    };
  }

  componentToDisplay(vnode: m.Vnode<null, State>): m.Children {
    this.parseScmsLink(sm);
    const scrollOptions: PluggableSCMScrollOptions = {
      sm,
      shouldOpenEditView: this.operation === "edit"
    };
    let noPluginMsg;
    if (!this.isPluginInstalled(vnode)) {
      noPluginMsg = <NoPluginsOfTypeInstalled extensionType={new SCMExtensionType()}/>;
    }
    const filteredScms: Stream<Scms> = Stream();
    if (vnode.state.searchText()) {
      const results = _.filter(vnode.state.scms(), (scm: Scm) => scm.name().toLowerCase().includes(vnode.state.searchText().toLowerCase()));

      if (_.isEmpty(results)) {
        return <div>
          <FlashMessage type={MessageType.info}>No Results for the search string: <em>{vnode.state.searchText()}</em></FlashMessage>
        </div>;
      }
      filteredScms(results);
    } else {
      filteredScms(vnode.state.scms());
    }
    return <div>
      {noPluginMsg}
      <FlashMessage type={this.flashMessage.type} message={this.flashMessage.message}/>
      <PluggableScmsWidget {...vnode.state} scms={filteredScms} scrollOptions={scrollOptions}/>
    </div>;
  }

  pageName(): string {
    return "Pluggable SCMs";
  }

  fetchData(vnode: m.Vnode<null, State>): Promise<any> {
    return Promise.all([PluggableScmCRUD.all(), PluginInfoCRUD.all({type: ExtensionTypeString.SCM})])
                  .then((result) => {
                    result[0].do((successResponse) => {
                      vnode.state.scms(successResponse.body);
                      this.pageState = PageState.OK;
                    }, (errorResponse) => {
                      this.flashMessage.setMessage(MessageType.alert, JSON.parse(errorResponse.body!).message);
                      this.pageState = PageState.FAILED;
                    });

                    result[1].do((successResponse) => {
                      vnode.state.pluginInfos(successResponse.body);
                      this.pageState = PageState.OK;
                    }, (errorResponse) => {
                      this.flashMessage.setMessage(MessageType.alert, JSON.parse(errorResponse.body!).message);
                      this.pageState = PageState.FAILED;
                    });
                  });
  }

  helpText(): m.Children {
    return PluggableScmsWidget.helpText();
  }

  protected headerPanel(vnode: m.Vnode<null, State>): any {
    const buttons = [
      <Primary icon={ButtonIcon.ADD} disabled={!this.isPluginInstalled(vnode)}
               onclick={vnode.state.onAdd}>
        Create Pluggable Scm
      </Primary>
    ];
    if (!_.isEmpty(vnode.state.scms())) {
      const searchBox = <div className={configRepoStyles.wrapperForSearchBox}>
        <SearchField property={vnode.state.searchText} dataTestId={"search-box"}
                     placeholder="Search for a pluggable scm name"/>
      </div>;
      buttons.splice(0, 0, searchBox);
    }
    return <HeaderPanel title={this.pageName()} buttons={buttons} help={this.helpText()}/>;
  }

  private isPluginInstalled(vnode: m.Vnode<null, State>) {
    return vnode.state.pluginInfos().length !== 0;
  }

  private parseScmsLink(sm: ScrollManager) {
    sm.setTarget(m.route.param().name || "");
    this.operation = (m.route.param().operation || "").toLowerCase();
  }
}
