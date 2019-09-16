Troxy.registerView("recordings", Backbone.View.extend({
    codeEditors: {},
    requestRegexes: [
        {
            value: "\\d+",
            meta: "1+ tall"
        },
        {
            value: "[^<]+",
            meta: "1+ XML innhold"
        },
        {
            value: "[^\"]+",
            meta: "1+ XML attributt"
        },
        {
            value: ".*",
            meta: "0+ alle tegn"
        }
    ],

    initialize: function() {
        // add "?<name>" to regexes
        var regexCount = this.requestRegexes.length;
        for (var regexIndex = 0; regexIndex < regexCount; ++regexIndex) {
            var regexEntry = this.requestRegexes[regexIndex];
            var newRegexEntry = {
                value: "(?<navn>" + regexEntry.value + ")",
                meta: regexEntry.meta
            };
            this.requestRegexes.push(newRegexEntry);
        }
        var that = this;
        $.get("/views/recordings/recordings.html", function(html) {
            that.$el.html(html);

            // set up tab clicks (for clicks on current/original recording tabs)
            var tabs = $(".recording-tab");
            tabs.click(function() {
                tabs.removeClass("selected");
                $(this).addClass("selected");
                that.updateEditors();
            });

            // setup delete response button
            var deleteResponseButton = $("#delete-response-button");
            deleteResponseButton.click(function() {
                var selected = $(".response-tab.selected")[0];
                selected = selected ? selected.tabIndex : -1;
                if (selected > 0 && selected < that.recording.responseTemplates.length) {
                    for (var i = selected; i < that.recording.responseTemplates.length - 1; ++i) {
                        that.recording.responseTemplates[i] = JSON.parse(JSON.stringify(that.recording.responseTemplates[i + 1]));
                    }
                    that.recording.responseTemplates.pop();
                    that.updateEditors();
                    $("#save-recording-button").removeClass("saved-ok");
                }
            });
            deleteResponseButton.hide(); // XXX: this is a hack, for some unknown reason the button is smaller if it's not visible on page creation

            // setup save button
            $("#save-recording-button").click(function() {
                that.saveRecording();
            });

            // use jQuery to detect when fields are changed, "onchange" and "onkeypress" won't detect pasting into fields
            $("#edit-recordings-container").on("input", function() {
                that.updateRecording();
            });
            // resize textarea to fit text
            $("#recording-comment").on("input", function() {
                that.resizeRecordingComment();
            });

            // save recording with ctrl-s
            $(window).on("keydown", function(e) {
                if ($("#view-recordings").is(":visible") && (event.ctrlKey || event.metaKey)) {
                    if (String.fromCharCode(e.which).toLowerCase() === 's') {
                        e.preventDefault();
                        that.saveRecording();
                    }
                }
            });

            // setup Ace code editors
            var langTools = ace.require('ace/ext/language_tools');
            var troxyCompleter = {
                getCompletions: function(editor, session, pos, prefix, callback) {
                    if ($("#request-tab").hasClass("selected")) {
                        // autocompleter for request
                        var selectedText = editor.getSelectedText();
                        if (selectedText !== "") {
                            var matchedRegexes = [];
                            for (var i = 0; i < that.requestRegexes.length; ++i) {
                                var regex = that.requestRegexes[i].value.replace(/\?<.*>/, ""); // named groups not supported in javascript, remove them
                                if (selectedText.match(regex)) {
                                    matchedRegexes.push(that.requestRegexes[i]);
                                }
                            }
                            callback(null, matchedRegexes);
                        } else {
                            callback(null, that.requestRegexes);
                        }
                    } else {
                        // autocompleter for response
                        // TODO: limit entries by implementing our own prefix-finding thingy
                        if (!that.responseVariables) {
                            // find all grouped regexes in request and create response variables
                            var findGroupRegex = /\((\?<([a-zA-Z0-9]+)>)?.*\)/g;
                            that.responseVariables = [];
                            for (var key in that.recording.requestPattern) {
                                if (!that.recording.requestPattern.hasOwnProperty(key) || key == "original") {
                                    continue;
                                }
                                var result;
                                var index = 0;
                                while (result = findGroupRegex.exec(that.recording.requestPattern[key])) {
                                    index++;
                                    var entryMeta = ""; // TODO: show matched value in original request as comment. i.e. "$ssn - 12312312312"
                                    var entry = {
                                        value: "$" + key + ":" + (result[2] ? result[2] : index) + "$",
                                        meta: entryMeta
                                    };
                                    that.responseVariables.push(entry);
                                }
                            }
                        }
                        callback(null, that.responseVariables);
                    }
                }
            };
            langTools.setCompleters([troxyCompleter]);

            that.codeEditors.header = ace.edit("header-editor");
            that.codeEditors.header.setTheme("ace/theme/dawn");
            that.codeEditors.header.setShowPrintMargin(false);
            that.codeEditors.header.setOptions({
                fontSize: "10pt",
                enableBasicAutocompletion: true,
                minLines: 1,
                maxLines: Infinity
            });
            that.codeEditors.header.on("change", function() {
                that.updateRecording();
            });
            that.codeEditors.header.$blockScrolling = Infinity; // TODO: this is to avoid a spamming warning about deprecated use, we need to fix this
            that.codeEditors.header.$defaultCompletionIdentifierRegex = /a^/; // TODO: we've modified Ace to use this config, see https://github.com/ajaxorg/ace/pull/2905
            that.codeEditors.header.renderer.setScrollMargin(4, 6); // XXX: this cause the editor to loop infinitely when lines exceed maxLines (if this is set to something else than "Infinity")

            that.codeEditors.content = ace.edit("content-editor");
            that.codeEditors.content.setTheme("ace/theme/dawn");
            that.codeEditors.content.setShowPrintMargin(false);
            that.codeEditors.content.setOptions({
                fontSize: "10pt",
                enableBasicAutocompletion: true,
                minLines: 1,
                maxLines: Infinity
            });
            that.codeEditors.content.on("change", function() {
                that.updateRecording();
            });
            that.codeEditors.content.$blockScrolling = Infinity; // TODO: this is to avoid a spamming warning about deprecated use, we need to fix this
            that.codeEditors.content.$defaultCompletionIdentifierRegex = /a^/; // TODO: we've modified Ace to use this config, see https://github.com/ajaxorg/ace/pull/2905
            that.codeEditors.content.renderer.setScrollMargin(4, 6); // XXX: this cause the editor to loop infinitely when lines exceed maxLines (if this is set to something else than "Infinity")

            // setup file tree buttons
            $("#action-activate").click(function() {
                that.setRecordingActivation(true);
            });
            $("#action-deactivate").click(function() {
                that.setRecordingActivation(false);
            });
            $("#action-refresh").click(function() {
                that.updateRecordingsList(true);
            });
            $("#action-rename").click(function() {
                var jsTree = $("#recording-tree-container").jstree(true);
                var toMove = [];
                $.each(jsTree.get_selected(), function(index, id) {
                    toMove.push(id);
                });
                if (toMove.length != 1) {
                    bootbox.alert("Kan ikke endre navn på flere opptak/kataloger samtidig");
                    return;
                }
                bootbox.prompt({
                    title: "Nytt navn",
                    value: toMove[0],
                    callback: function(target) {
                        if (target) {
                            that.moveDirectoryOrRecording(toMove[0], target);
                        }
                    }
                });
            });
            $("#action-copy").click(function() {
                var jsTree = $("#recording-tree-container").jstree(true);
                var toCopy = [];
                $.each(jsTree.get_selected(), function(index, id) {
                    toCopy.push(id);
                });
                if (toCopy.length != 1) {
                    bootbox.alert("Kan ikke kopiere flere opptak/kataloger samtidig");
                    return;
                }
                bootbox.prompt({
                    title: "Nytt navn",
                    value: toCopy[0],
                    callback: function(target) {
                        if (target) {
                            that.copyDirectoryOrRecording(toCopy[0], target);
                        }
                    }
                });
            });
            $("#action-new-folder").click(function() {
                that.createDirectoryOrRecording();
            });
            $("#action-delete").click(function() {
                that.deleteDirectoryOrRecordings();
            });
            $("#action-download").click(function() {
                that.downloadDirectoryOrRecordings();
            });
            $("#action-upload").click(function() {
                that.uploadFile();
            });

            // setup file tree
            $("#recording-tree-container").jstree({
                "core": {
                    "check_callback": true
                },
                "plugins": ["dnd"]
            }).on("move_node.jstree", function(e, data) {
                var jsTree = $("#recording-tree-container").jstree(true);
                var source = jsTree.get_node(data.node).id;
                var target = jsTree.get_node(data.parent).id;
                if (!target.endsWith("/")) {
                    target = target.substring(0, target.lastIndexOf("/") + 1);
                }
                target = target + source.substring(source.lastIndexOf("/", source.length - 2) + 1);
                that.moveDirectoryOrRecording(source, target);
            }).on("copy_node.jstree", function(e, data) {
                var jsTree = $("#recording-tree-container").jstree(true);
                var source = jsTree.get_node(data.original).id;
                var target = jsTree.get_node(data.parent).id;
                if (!target.endsWith("/")) {
                    target = target.substring(0, target.lastIndexOf("/") + 1);
                }
                target = target + source.substring(source.lastIndexOf("/", source.length - 2) + 1);
                that.copyDirectoryOrRecording(source, target);
            }).on("dblclick.jstree", function(e) {
                // doubleclicked node in recording tree, load if recording
                var node = $(e.target).closest("li")[0];
                if (node == null) {
                    // no node selected
                    return;
                }
                var id = node.id;
                if (id.endsWith("/")) {
                    // this is a directory
                    return;
                }
                var tmpLoadRecording = function() {
                    var jsTree = $("#recording-tree-container").jstree(true);
                    // set previous icon for recording we're editing
                    if (that.recording && that.recording.filename) {
                        jsTree.set_icon(that.recording.filename, that.current_editing_last_icon);
                    }
                    // load recording
                    that.loadRecording(id);
                    // set new editing icon
                    that.current_editing_last_icon = jsTree.get_icon(id);
                    if (typeof that.current_editing_last_icon ===  "string" && that.current_editing_last_icon.endsWith("code_activated.png")) {
                        jsTree.set_icon(id, "/views/recordings/images/tree/code_editing_activated.png");
                    } else {
                        jsTree.set_icon(id, "/views/recordings/images/tree/code_editing.png");
                    }

                    // update jstree data to prevent list from being refreshed upon saving recording
                    var updateIcon = function(parent, id, icon) {
                        $.each(parent.children, function(index, entry) {
                            if (entry.id == id) {
                                entry.icon = icon;
                            }
                            if (entry.children) {
                                updateIcon(entry, id, icon);
                            }
                        });
                    };
                    updateIcon(jsTree.settings.core.data, id, jsTree.get_icon(id));
                };
                if (!$("#save-recording-button").hasClass("saved-ok")) {
                    bootbox.confirm("Opptak '" + that.recording.filename + "' er endret, men ikke lagret. Redigere nytt opptak vil føre til at endringene mistes. Redigere nytt opptak?", function(result) {
                        if (result) {
                            tmpLoadRecording();
                        }
                    });
                } else {
                    tmpLoadRecording();
                }
            }).on("open_node.jstree", function(e, data) {
                var jsTree = $("#recording-tree-container").jstree(true);
                var activated = jsTree.get_icon(data.node.id) == "/views/recordings/images/tree/folder_activated.png";
                jsTree.set_icon(data.node.id, activated ? "/views/recordings/images/tree/folder_activated_open.png" : "/views/recordings/images/tree/folder_open.png");
            }).on("close_node.jstree", function(e, data) {
                var jsTree = $("#recording-tree-container").jstree(true);
                var activated = jsTree.get_icon(data.node.id) == "/views/recordings/images/tree/folder_activated_open.png";
                jsTree.set_icon(data.node.id, activated ? "/views/recordings/images/tree/folder_activated.png" : "/views/recordings/images/tree/folder.png");
            }).on("keyup.jstree", function(e) {
                if (e.keyCode == 46) {
                    // delete key, delete selected recordings
                    that.deleteDirectoryOrRecordings();
                } else if (e.keyCode == 65) {
                    // "a" key, activate selected recordings
                    that.setRecordingActivation(true);
                } else if (e.keyCode == 68) {
                    // "d" key, deactivate selected recordings
                    that.setRecordingActivation(false);
                }
            }).on("refresh.jstree", function() {
                // set focus to recordings tree when it's refreshed
                // do note the «tabindex="-1"» for the div "recording-tree-container", this allows us to set focus to the div
                $("#recording-tree-container").focus();
            });

            // setup drag handle for resizing recordings list view
            var dragHandle = $("#drag-handle");
            var dragHandleWidth = dragHandle.width();
            var minX = 400 - dragHandleWidth / 2;
            dragHandle.on("drag", function(e) {
                var x = e.originalEvent.pageX - dragHandleWidth / 2;
                if (x <= 0) {
                    return; // when we let go of mouse a final event with pageX set to 0 is sent, skip this
                }
                if (x < minX) {
                    x = minX;
                }
                var width = x + dragHandleWidth / 2;

                var recordingsListContainer = $("#load-files-container");
                var editRecordingsContainer = $("#edit-recordings-container");

                recordingsListContainer.css({width: width + 'px'});
                dragHandle.css({left: (width - dragHandleWidth / 2) + 'px'});
                editRecordingsContainer.css({left: width + 'px'});
            });

            // let user enable/disable tab/indentation in header/content
            // disable by default
            that.codeEditors.header.commands.bindKey("Tab", null);
            that.codeEditors.header.commands.bindKey("Shift-Tab", null);
            that.codeEditors.content.commands.bindKey("Tab", null);
            that.codeEditors.content.commands.bindKey("Shift-Tab", null);
            $("#allow-tab-indentation").change(function() {
                if (this.checked) {
                    that.codeEditors.header.commands.bindKey("Tab", "indent");
                    that.codeEditors.header.commands.bindKey("Shift-Tab", "outdent");
                    that.codeEditors.content.commands.bindKey("Tab", "indent");
                    that.codeEditors.content.commands.bindKey("Shift-Tab", "outdent");
                } else {
                    that.codeEditors.header.commands.bindKey("Tab", null);
                    that.codeEditors.header.commands.bindKey("Shift-Tab", null);
                    that.codeEditors.content.commands.bindKey("Tab", null);
                    that.codeEditors.content.commands.bindKey("Shift-Tab", null);
                }
            });

            that.updateRecordingsList();
        });
    },

    onShow: function() {
        this.updateRecordingsList();
    },

    setRecordingActivation: function(activated) {
        var jsTree = $("#recording-tree-container").jstree(true);
        var recordings = {};
        $.each(jsTree.get_selected(), function(index, id) {
            if (id === "/") {
                id = ""; // if root node is selected we want all recordings to be matched
            }
            recordings[id] = activated;
        });
        var that = this;
        $.ajax({
            url: "api/recordings",
            type: "PUT",
            contentType: "application/json",
            dataType: "json",
            data: JSON.stringify(recordings),
            success: function(response) {
                that.updateRecordingsList();
                response.loaded = response.loaded || 0;
                response.skipped = response.skipped || 0;
                var total = response.loaded + response.skipped;
                if (activated) {
                    that.showMessage(response.loaded + (total == 1 ? " valgt fil" : " av " + total + " valgte filer") + " aktivert");
                } else {
                    that.showMessage("valgt" + (total == 1 ? " fil" : "e filer") + " deaktivert");
                }
            },
            error: function(jqXhr, textStatus, errorThrown) {
                bootbox.alert("En feil oppstod ved endring av aktiverte opptak.<br/>Status: " + textStatus + "<br />Årsak: " + errorThrown);
                that.updateRecordingsList(true);
            }
        });
    },

    showMessage: function(message) {
        var recordingList = $("#recording-tree-container");
        var messagePane = $("#recording-message-pane");
        var id = "message-" + Date.now();
        var span = $("<span id='" + id + "' class='recording-message-span'>" + message + "</span>");
        recordingList.animate({
            top: "+=25px"
        }, 500, function() {
            messagePane.prepend(span);
            setTimeout(function() {
                $("#" + id).remove();
                recordingList.animate({
                    top: "-=25px"
                }, 500);
            }, 5000);
        });
    },

    updateRecordingsList: function(force) {
        var that = this;
        $.ajax({
            url: "api/recordings",
            type: "GET",
            success: function(response) {
                var keys = Object.keys(response);
                keys.sort(function(a, b) {
                    return a.toLowerCase().localeCompare(b.toLowerCase());
                });
                // count amount of files and activated recordings in directories
                var dirDetails = {};
                $.each(keys, function(keyIndex, key) {
                    var parts = key.split("/");
                    if (parts != null) {
                        for (var i1 = parts.length - 1; i1 >= 0; --i1) {
                            var dir = "";
                            for (var i2 = 0; i2 < i1; ++i2) {
                                dir = dir + parts[i2] + "/";
                            }
                            if (!(dir in dirDetails)) {
                                dirDetails[dir] = {
                                    files: 0,
                                    activated: 0
                                };
                            }
                            if (key != "" && !key.endsWith("/")) {
                                dirDetails[dir].files = dirDetails[dir].files + 1;
                                if (response[key]) {
                                    dirDetails[dir].activated = dirDetails[dir].activated + 1;
                                }
                            }
                        }
                    }
                });
                var tree = {
                    text: "root (" + dirDetails[""].activated + "/" + dirDetails[""].files + ")",
                    id: "/",
                    state: {
                        opened: true
                    },
                    icon: "/views/recordings/images/tree/folder_open.png",
                    children: []
                };
                $.each(keys, function(keyIndex, key) {
                    var parts = key.split("/");
                    var children = tree.children;
                    $.each(parts, function(partIndex, part) {
                        if (partIndex < parts.length - 1) {
                            // node with children
                            var dirDetailsKey = parts[0];
                            for (var i = 1; i <= partIndex; ++i) {
                                dirDetailsKey += "/" + parts[i];
                            }
                            dirDetailsKey += "/";
                            var details = dirDetails[dirDetailsKey];
                            var nodeText = part + (details != null ? " (" + details.activated + "/" + details.files + ")" : "");
                            var childPos = children.map(function(object) { return object.text; }).indexOf(nodeText);
                            if (childPos < 0) {
                                var dirNode = {
                                    id: key,
                                    text: nodeText,
                                    children: []
                                };
                                if (partIndex == parts.length - 2 && parts[parts.length - 1] == "") {
                                    // display leaf nodes that actually are directories as directories rather than leaf nodes
                                    dirNode.icon = "/views/recordings/images/tree/folder.png";
                                }
                                if (response[key]) {
                                    dirNode.icon = "/views/recordings/images/tree/folder_activated.png";
                                }
                                children.push(dirNode);
                                children = children[children.length - 1].children;
                            } else {
                                children = children[childPos].children;
                            }
                        } else if (part != "") {
                            // possible leaf node
                            var fileNode = {
                                id: key,
                                text: part
                            };
                            if (that.recording && key == that.recording.filename) {
                                that.current_editing_last_icon = "/views/recordings/images/tree/code" + (response[key] ? "_activated.png" : ".png");
                                fileNode.icon = "/views/recordings/images/tree/code_editing" + (response[key] ? "_activated.png" : ".png");
                            } else if (response[key]) {
                                fileNode.icon = "/views/recordings/images/tree/code_activated.png";
                            }
                            children.push(fileNode);
                        }
                    });
                });
                var jsTree = $("#recording-tree-container").jstree(true);
                if (force || JSON.stringify(jsTree.settings.core.data) !== JSON.stringify(tree)) {
                    // content changed, refresh tree
                    jsTree.settings.core.data = tree;
                    jsTree.refresh();
                }
            },
            error: function(jqXhr, textStatus, errorThrown) {
                bootbox.alert("Kunne ikke hente liste over opptak fra Troxy.<br/>Status: " + textStatus + "<br />Årsak: " + errorThrown);
            }
        });
    },

    updateRecording: function() {
        if ($("#original-tab").hasClass("selected")) {
            return; // displaying original recording, don't modify data
        }
        if (!this.recording) {
            return; // no recording loaded
        }
        function getIntValue(input) {
            return input != "" && !isNaN(input) ? Math.min(Math.max(parseInt(input, 10), 0), 3600000) : 0;
        }
        this.recording.responseStrategy = $("#recording-response-strategy").val();
        this.recording.comment = $("#recording-comment").val();
        var selectedResponse = $(".response-tab.selected")[0];
        if (selectedResponse) {
            var index = selectedResponse.tabIndex;
            this.recording.responseTemplates[index].code = $("#code").val();
            this.recording.responseTemplates[index].header = this.codeEditors.header.getValue();
            this.recording.responseTemplates[index].content = this.codeEditors.content.getValue();
            var delayStrategy = $("#delay-strategy");
            this.recording.responseTemplates[index].delayStrategy = delayStrategy.val();
            // setup delay strategy dropdown
            var that = this;
            delayStrategy.change(function() {
                that.recording.responseTemplates[index].delayStrategy = $("#delay-strategy").val();
                that.updateEditors();
                that.updateRecording();
            });
            if (["RANDOM", "NORMAL", "EXPONENTIAL"].indexOf(this.recording.responseTemplates[index].delayStrategy) != -1) {
                var min_delay = $("#delay-value-MIN-inp");
                this.recording.responseTemplates[index].delayMin = getIntValue(min_delay.val());
                min_delay.val(this.recording.responseTemplates[index].delayMin);
                var max_delay = $("#delay-value-MAX-inp");
                this.recording.responseTemplates[index].delayMax = getIntValue(max_delay.val());
                max_delay.val(this.recording.responseTemplates[index].delayMax);
            }
            if (["FIXED", "EXPONENTIAL"].indexOf(this.recording.responseTemplates[index].delayStrategy) != -1) {
                var mean_delay = $("#delay-value-MEAN-inp");
                this.recording.responseTemplates[index].delayMean = getIntValue(mean_delay.val());
                mean_delay.val(this.recording.responseTemplates[index].delayMean);
            }
            if (this.recording.responseTemplates.length >= 1) {
                var weight = $("#weight-value-inp");
                this.recording.responseTemplates[index].weight = getIntValue(weight.val());
                weight.val(this.recording.responseTemplates[index].weight);
            }
            this.updateWeightValueText();
        } else {
            this.recording.requestPattern.protocol = "^" + $("#protocol").val() + "$";
            this.recording.requestPattern.host = "^" + $("#host").val() + "$";
            this.recording.requestPattern.port = "^" + $("#port").val() + "$";
            this.recording.requestPattern.path = "^" + $("#path").val() + "$";
            this.recording.requestPattern.query = "^" + $("#query").val() + "$";
            this.recording.requestPattern.method = "^" + $("#method").val() + "$";
            this.recording.requestPattern.header = "^" + this.codeEditors.header.getValue() + "$";
            this.recording.requestPattern.content = "^" + this.codeEditors.content.getValue() + "$";
        }
        $("#save-recording-button").removeClass("saved-ok");
    },

    resizeRecordingComment: function() {
        var textarea = $("#recording-comment");
        textarea.css('height', '0px');
        textarea.css('height', Math.min(textarea[0].scrollHeight) + 'px');
    },

    updateWeightValueText: function() {
        var selectedResponse = $(".response-tab.selected")[0];
        var weightText;
        if (this.recording.responseTemplates.length < 1) {
            weightText = "";
        } else if (this.recording.responseTemplates[selectedResponse.tabIndex].weight == 0) {
            weightText = "Denne responsen vil ikke bli returnert";
        } else if (this.recording.responseStrategy == "SEQUENTIAL") {
            weightText = "Denne responsen vil bli returnert "
                + this.recording.responseTemplates[selectedResponse.tabIndex].weight + " ganger før neste respons i opptaket blir returnert.";
        } else if (this.recording.responseStrategy == "RANDOM") {
            var totalWeight = 0;
            for (var i = 0; i < this.recording.responseTemplates.length; ++i) {
                totalWeight += this.recording.responseTemplates[i].weight;
            }
            weightText = "Denne responsen vil bli returnert "
                + this.recording.responseTemplates[selectedResponse.tabIndex].weight + " av " + totalWeight
                + " (" + Math.round(this.recording.responseTemplates[selectedResponse.tabIndex].weight * 1000 / totalWeight) / 10 + "%)"
                + " ganger i et tilfeldig normalfordelt mønster.";
        } else {
            weightText = "Ukjent responsstrategi";
        }
        $("#weight-value-text").text(weightText);
    },

    updateEditors: function() {
        if (!this.recording) {
            return;
        }

        // remove coloring of fields
        var protocolField = $("#protocol");
        var hostField = $("#host");
        var methodField = $("#method");
        var pathField = $("#path");
        var portField = $("#port");
        var queryField = $("#query");
        protocolField.css("background", "");
        hostField.css("background", "");
        methodField.css("background", "");
        pathField.css("background", "");
        portField.css("background", "");
        queryField.css("background", "");
        $("#header-editor").css("background", "");
        $("#content-editor").css("background", "");

        // set response strategy & comment
        var originalTab = $("#original-tab");
        var showOriginal = originalTab.hasClass("selected");
        $("#recording-response-strategy").val(this.recording.responseStrategy).attr("disabled", showOriginal);
        $("#recording-comment").val(this.recording.comment).attr("readonly", showOriginal);
        this.resizeRecordingComment();

        // find selected response tab (if any)
        var responseTabs = $(".response-tab");
        var selectedTab = responseTabs.filter(".selected");
        var selected = (selectedTab[0] && selectedTab[0].tabIndex < this.recording.responseTemplates.length) ? selectedTab[0].tabIndex : -1;
        var parent = responseTabs.parent();
        responseTabs.remove();
        // setup response tabs
        for (var i = 0; i < this.recording.responseTemplates.length; ++i) {
            var responseTab = $("<div></div>").addClass("recording-tab-button").addClass("tab-button").addClass("response-tab");
            if (selected <= 0) {
                $("#delete-response-button").hide();
            }
            if (selected == i) {
                responseTab.addClass("selected");
                if (!showOriginal && i > 0) {
                    $("#delete-response-button").show();
                } else {
                    $("#delete-response-button").hide();
                }
            }
            responseTab.attr("tabindex", i);
            responseTab.text("Response " + (i + 1));
            parent.append(responseTab);
        }
        if (!showOriginal) {
            var newResponseTab = $("<div></div>").addClass("recording-tab-button").addClass("tab-button").addClass("response-tab");
            newResponseTab.attr("tabindex", this.recording.responseTemplates.length);
            newResponseTab.text(selected == -1 ? "[Ny response]" : "[Klon response]");
            parent.append(newResponseTab);
        }
        // setup tab clicks
        var tabButtons = $(".recording-tab-button");
        var that = this;
        tabButtons.off("click"); // remove old click handlers
        tabButtons.click(function() {
            var previous = $(".response-tab.selected")[0];
            previous = previous ? previous.tabIndex : -1;
            tabButtons.removeClass("selected");
            $(this).addClass("selected");
            var selected = $(this)[0].tabIndex;
            if (selected >= 0 && !that.recording.responseTemplates[selected]) {
                // new response, duplicate response from previously selected response
                if (previous >= 0) {
                    that.recording.responseTemplates[selected] = JSON.parse(JSON.stringify(that.recording.responseTemplates[previous]));
                } else {
                    // no response for recording at all? create a blank response
                    that.recording.responseTemplates[selected] = {
                        code: "",
                        header: "",
                        content: ""
                    };
                }
                $("#save-recording-button").removeClass("saved-ok");
            }
            // show delete button when displaying response 2 and higher, hide otherwise
            if (selected > 0 && selected < that.recording.responseTemplates.length) {
                $("#delete-response-button").show();
            } else {
                $("#delete-response-button").hide();
            }
            if (previous != selected) {
                that.responseVariables = null;
                that.updateEditors();
            }
        });

        // setup editors
        var data = this.recording.requestPattern;
        // hide "original" tab when we don't have the original recording
        if (data.originalRequest) {
            originalTab.show();
        } else {
            originalTab.hide();
        }

        var request = true;
        // set filename
        $("#recording-filename").val(this.recording.filename);

        var selectedResponse = $(".response-tab.selected")[0];
        if (selectedResponse) {
            request = false;
            data = this.recording.responseTemplates[selectedResponse.tabIndex];
            $("#request-editor").hide();
            $("#response-editor").show();
            // set delay strategy
            $("#delay-strategy").val(this.recording.responseTemplates[selectedResponse.tabIndex].delayStrategy ? this.recording.responseTemplates[selectedResponse.tabIndex].delayStrategy : "NONE");
            // set and show/hide delay strategy values
            $("#delay-value-MIN-inp").val(this.recording.responseTemplates[selectedResponse.tabIndex].delayMin);
            $("#delay-value-MEAN-inp").val(this.recording.responseTemplates[selectedResponse.tabIndex].delayMean);
            $("#delay-value-MAX-inp").val(this.recording.responseTemplates[selectedResponse.tabIndex].delayMax);
            var delayValuesLabel = $("#delay-values-label");
            delayValuesLabel.hide();
            var delayValues = $(".delay-value");
            delayValues.hide();
            if (["RANDOM", "NORMAL", "EXPONENTIAL"].indexOf(this.recording.responseTemplates[selectedResponse.tabIndex].delayStrategy) != -1) {
                delayValuesLabel.show();
                $("#delay-value-MIN").show();
                $("#delay-value-MAX").show();
            }
            if (["FIXED", "EXPONENTIAL"].indexOf(this.recording.responseTemplates[selectedResponse.tabIndex].delayStrategy) != -1) {
                delayValuesLabel.show();
                $("#delay-value-MEAN").show();
            }
            $("#weight-value-inp").val(this.recording.responseTemplates[selectedResponse.tabIndex].weight);
            this.updateWeightValueText();
        } else {
            $("#request-tab").addClass("selected");
            $("#request-editor").show();
            $("#response-editor").hide();
        }
        if (request) {
            if (showOriginal) {
                data = data.originalRequest;
            }
            protocolField.val(this.removeHatAndDollar(data.protocol)).attr("readonly", showOriginal);
            hostField.val(this.removeHatAndDollar(data.host)).attr("readonly", showOriginal);
            portField.val(this.removeHatAndDollar(data.port)).attr("readonly", showOriginal);
            pathField.val(this.removeHatAndDollar(data.path)).attr("readonly", showOriginal);
            queryField.val(this.removeHatAndDollar(data.query)).attr("readonly", showOriginal);
            methodField.val(this.removeHatAndDollar(data.method)).attr("readonly", showOriginal);
        } else {
            if (showOriginal) {
                data = data.originalResponse;
            }
            $("#code").val(this.removeHatAndDollar(data.code)).attr("readonly", showOriginal);
        }
        this.updateCodeEditor(this.codeEditors.header, this.removeHatAndDollar(data.header), showOriginal, "ace/mode/properties");
        this.updateCodeEditor(this.codeEditors.content, this.removeHatAndDollar(data.content), showOriginal, "ace/mode/xml");

        // TODO: remove when Troxy no longer supports .xml-format
        // update filetree if extension changed from .xml to .troxy
        var jsTree = $("#recording-tree-container").jstree(true);
        var tmpSelected = [];
        $.each(jsTree.get_selected(), function(index, id) {
            tmpSelected.push(id);
        });
        if (tmpSelected.length == 1) {
            if (tmpSelected[0] != this.recording.filename) {
                this.updateRecordingsList();
            }
        }
        // TODO: END
    },

    updateCodeEditor: function(codeEditor, value, readonly, mode) {
        var editSession = ace.createEditSession(value, mode);
        editSession.setUseWrapMode(true);
        codeEditor.setSession(editSession);
        codeEditor.setOption("readOnly", readonly);
        codeEditor.setOption("useWorker", false);
    },

    removeHatAndDollar: function(text) {
        if (text.length > 0 && text[0] === '^') {
            text = text.substring(1);
        }
        if (text.length > 0 && text[text.length - 1] === '$') {
            text = text.substring(0, text.length - 1);
        }
        return text;
    },

    loadRecording: function(filename) {
        var that = this;
        $.get("/api/recordings/" + encodeURIComponent(filename), function(recording) {
            that.recording = recording;

            // set current version as selected tab when loading a recording
            $(".recording-tab").removeClass("selected");
            $("#current-tab").addClass("selected");

            // set "Request" as selected tab when loading a recording
            $(".response-tab").removeClass("selected");
            $("#request-tab").addClass("selected");

            // and hide button for deleting a response
            $("#delete-response-button").hide();

            // and update editors
            that.updateEditors();
            $("#save-recording-button").addClass("saved-ok");
        });
    },

    saveRecording: function() {
        // TODO: check that we actually have a recording, and that we've actually modified it
        var that = this;
        $.ajax({
            url: "api/recordings/" + encodeURIComponent(that.recording.filename),
            type: "PUT",
            contentType: "application/json",
            dataType: "json",
            data: JSON.stringify(that.recording),
            success: function(response) {
                that.updateRecordingsList();
                $("#save-recording-button").addClass("saved-ok");
                // display regex times or any other issues
                if (response.match_status == -2) {
                    // somehow we're unable to read the file as a recording after saving it. this is not good at all
                    bootbox.alert("Kunne ikke lese opptak etter lagring. Opptaksfilen er trolig korrumpert!");
                } else if (response.match_status == -1) {
                    // file doesn't have an original recording. can't test regular expressions
                } else if (response.match_status == 0) {
                    // file can be read and have an original recording
                    // we can check how much time it takes matching regular expressions with original request
                    var markField = function(field, ms) {
                        var color = "";
                        var title = "Det tok " + ms + "ms å sammenligne regulære uttrykk med originalt opptak for dette feltet";
                        if (ms == null) {
                            title = "Opptak har ikke original request, kan ikke sammenligne regulære uttrykk med original request";
                        } else if (ms == -1) {
                            title = "Regulært uttrykk treffer ikke data i originalt opptak for dette feltet";
                            color = "orange";
                        } else if (ms > 50) {
                            color = "yellow";
                        } else {
                            color = "lightgreen";
                        }
                        field.css("background", color).attr("title", title);
                        field.delay(3000).animate({
                            backgroundColor: "white"
                        });
                    };
                    markField($("#protocol"), response.protocol);
                    markField($("#host"), response.host);
                    markField($("#method"), response.method);
                    markField($("#path"), response.path);
                    markField($("#port"), response.port);
                    markField($("#query"), response.query);
                    markField($("#header-editor"), response.header);
                    markField($("#content-editor"), response.content);
                } else {
                    // unknown match status
                    bootbox.alert("Ukjent status returnert ved testing av opptak: " + response.match_status);
                }
            },
            error: function(jqXhr, textStatus, errorThrown) {
                bootbox.alert("Lagring av opptak feilet. Se logg for ytterligere detaljer.<br/>Status: " + textStatus + "<br />Årsak: " + errorThrown);
                that.updateRecordingsList(true);
            }
        })
    },

    moveDirectoryOrRecording: function(source, target) {
        var that = this;
        $.ajax({
            url: "api/recordings_move/" + encodeURIComponent(source),
            type: "POST",
            data: target,
            success: function() {
                that.updateRecordingsList(true);
            },
            error: function(jqXhr, textStatus, errorThrown) {
                bootbox.alert("Flytte katalog eller opptak feilet.<br/>Status: " + textStatus + "<br />Årsak: " + errorThrown);
                that.updateRecordingsList(true);
            }
        })
    },

    copyDirectoryOrRecording: function(source, target) {
        var that = this;
        $.ajax({
            url: "api/recordings_copy/" + encodeURIComponent(source),
            type: "POST",
            data: target,
            success: function() {
                that.updateRecordingsList(true);
            },
            error: function(jqXhr, textStatus, errorThrown) {
                bootbox.alert("Kopiere katalog eller opptak feilet.<br/>Status: " + textStatus + "<br />Årsak: " + errorThrown);
                that.updateRecordingsList(true);
            }
        })
    },

    createDirectoryOrRecording: function() {
        var jsTree = $("#recording-tree-container").jstree(true);
        var parents = {};
        $.each(jsTree.get_selected(), function(index, id) {
            parents[id.endsWith("/") ? id : jsTree.get_parent(id).replace("#", "/")] = true;
        });
        var parent = Object.keys(parents).length > 0 ? Object.keys(parents)[0] : "/";
        var that = this;
        bootbox.prompt({
            title: "Navn på ny katalog/opptak under " + parent,
            callback: function(dirname) {
                if (!dirname) {
                    return;
                }
                var path = parent + dirname;

                $.ajax({
                    url: "api/recordings/" + encodeURIComponent(path),
                    type: "POST",
                    success: function() {
                        that.updateRecordingsList();
                    },
                    error: function(jqXhr, textStatus, errorThrown) {
                        bootbox.alert("Opprette katalog eller opptak feilet.<br/>Status: " + textStatus + "<br />Årsak: " + errorThrown);
                        that.updateRecordingsList(true);
                    }
                })
            }
        });
    },

    deleteDirectoryOrRecordings: function() {
        var jsTree = $("#recording-tree-container").jstree(true);
        var toDelete = [];
        $.each(jsTree.get_selected(), function(index, id) {
            toDelete.push(id);
        });
        var that = this;
        bootbox.confirm("Er du sikker på at du vil slette følgende kataloger/opptaksfiler?<br/>" + toDelete.join('<br/>'), function(result) {
            if (!result) {
                return;
            }
            toDelete.forEach(function(path) {
                $.ajax({
                    url: "api/recordings/" + encodeURIComponent(path),
                    type: "DELETE",
                    success: function() {
                        that.updateRecordingsList();
                    },
                    error: function(jqXhr, textStatus, errorThrown) {
                        bootbox.alert("Slette katalog eller opptak feilet.<br/>Status: " + textStatus + "<br />Årsak: " + errorThrown);
                        that.updateRecordingsList(true);
                    }
                })
            });
        });
    },

    downloadDirectoryOrRecordings: function() {
        var jsTree = $("#recording-tree-container").jstree(true);
        var form = $("#download-form");
        if (jsTree.get_selected().length > 0) {
            $.each(jsTree.get_selected(), function(index, id) {
                form.append('<input type="hidden" name="paths" value="' + id + '">');
            });
            form.submit();
            form.empty();
        }
    },

    uploadFile: function() {
        var jsTree = $("#recording-tree-container").jstree(true);
        var directory = jsTree.get_selected()[0];
        if (directory == null || !directory.endsWith("/") || jsTree.get_selected().length > 1) {
            bootbox.alert("Markér hvilken katalog filen skal lastes opp til.");
            return;
        }

        var html = "<div>" +
            "<form id='upload-form' action='/api/upload' method='POST' enctype='multipart/form-data'>" +
            "<input id='upload-directory' name='upload-directory' type='hidden' value='" + directory + "'>" +
            "<input id='upload-file' name='upload-file' type='file' multiple='true'>" +
            "</form>" +
            "</div>";

        var that = this;
        bootbox.dialog({
            message: html,
            title: "Last opp fil",
            buttons: {
                success: {
                    label: "Last opp",
                    callback: function() {
                        var formdata = new FormData();
                        //dialog.find('form')[0].submit();
                        formdata.append("directory", $("#upload-directory").val());
                        formdata.append("file", $("#upload-file")[0].files[0]);
                        $.ajax({
                            url: $("#upload-form").attr("action"),
                            type: "POST",
                            data: formdata,
                            processData: false,
                            contentType: false,
                            complete: function() {
                                that.updateRecordingsList();
                            }
                        });
                    }
                }
            }
        });
    }
}));
