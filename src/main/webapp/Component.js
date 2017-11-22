sap.ui.define([
  "sap/ui/core/UIComponent",
], function (UIComponent) {
  "use strict";
  return UIComponent.extend("org.github.tern.openui5.Component", {
    metadata: {
      manifest: "json"
    },
    init: function () {
      // call the init function of the parent
      UIComponent.prototype.init.apply(this, arguments);

      this.getRouter().initialize();
    }
  });
});