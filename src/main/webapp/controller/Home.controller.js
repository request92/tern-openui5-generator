sap.ui.define([
  "sap/ui/core/mvc/Controller",
  "sap/m/MessageToast"
], function (Controller, MessageToast) {
  "use strict";

  return Controller.extend("org.github.tern.openui5.controller.Home", {

    onInit : function () {
    },

    onDownload : function () {
      this.getView().setBusy(true);
      $.fileDownload("rest/services/download").done(function () {
        this.getView().setBusy(false);
        MessageToast.show("Download succeeded");
      }.bind(this)).fail(function () {
        this.getView().setBusy(false);
        MessageToast.show("Download failed");
      }.bind(this));
    }
  });
});