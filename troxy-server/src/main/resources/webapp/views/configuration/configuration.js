Troxy.registerView("configuration", Backbone.View.extend({
    initialize: function() {
        var that = this;
        $.get("/views/configuration/configuration.html", function(html) {
            that.$el.html(html);

            // set up configuration view
            that.codeEditor = ace.edit("config-editor");
            that.codeEditor.setTheme("ace/theme/dawn");
            that.codeEditor.setShowPrintMargin(false);
            that.codeEditor.getSession().setUseWrapMode(true);
            that.codeEditor.getSession().setMode("ace/mode/properties");
            that.codeEditor.commands.addCommand({
                name: 'save',
                bindKey: {win: "Ctrl-S", "mac": "Cmd-S"},
                exec: function() {
                    that.saveConfiguration();
                }
            });
            that.codeEditor.on("change", function() {
                that.toggleSaveButton(true);
            });
            that.codeEditor.$blockScrolling = Infinity; // TODO: this is to avoid a spamming warning about deprecated use, we need to fix this


            // set up buttons
            $("#save-configuration-button").click(function() {
                that.saveConfiguration();
            });
            $("#undo-configuration-button").click(function() {
                that.loadConfiguration();
            });

            // load configuration
            that.loadConfiguration();
        });
    },

    onShow: function() {
    },

    saveConfiguration: function() {
        var that = this;
        $.ajax({
            url: "api/configuration",
            type: "PUT",
            data: that.codeEditor.getValue(),
            success: function() {
                that.toggleSaveButton(false);
            },
            error: function(jqXhr, textStatus, errorThrown) {
                bootbox.alert("Kunne ikke lagre konfigurasjon for Troxy.<br/>Status: " + textStatus + "<br />Årsak: " + errorThrown);
            }
        })
    },

    loadConfiguration: function() {
        var that = this;
        $.ajax({
            url: "api/configuration",
            type: "GET",
            success: function(data) {
                that.codeEditor.session.setValue(data, 1);
                that.toggleSaveButton(false);
            },
            error: function(jqXhr, textStatus, errorThrown) {
                bootbox.alert("Lasting av konfigurasjon for Troxy feilet.<br/>Status: " + textStatus + "<br />Årsak: " + errorThrown);
            }
        });
    },

    toggleSaveButton: function(dirty) {
        var saveButton = $("#save-configuration-button");
        if (dirty) {
            saveButton.removeClass("saved-ok");
        } else {
            saveButton.addClass("saved-ok");
        }
    }
}));
