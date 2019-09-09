Troxy.registerView("log", Backbone.View.extend({
    events: {
        "change input#log-tail": "tailLog",
        "change input.log-level-filter": "setLogLinesVisibility"
    },

    logOffset: -1,
    logLimit: 1000,
    logTotal: -1,

    initialize: function() {
        var that = this;
        $.get("/views/log/log.html", function(html) {
            that.$el.html(html);

            // set up buttons
            $("#previous-page-button").click(function() {
                // load previous page
                var prevPageOffset = that.logOffset - that.logLimit;
                if (prevPageOffset < 0) {
                    prevPageOffset = 0;
                }
                $("#log-tail").prop("checked", false);
                that.getLogData(that.logLimit, prevPageOffset);
                var logContent = $("#log-content");
                logContent.scrollTop(logContent[0].scrollHeight);
            });
            $("#next-page-button").click(function() {
                // load next log page
                var nextPageOffset = that.logOffset + that.logLimit;
                $("#log-tail").prop("checked", false);
                that.getLogData(that.logLimit, nextPageOffset);
                var logContent = $("#log-content");
                logContent.scrollTop(0);
            });

            // load initial log
            that.getLogData();

            // tail log
            that.tailLog();
        });
    },

    onShow: function() {
    },

    tailLog: function() {
        var that = this;
        setTimeout(function() {
            var logTail = $("#log-tail");
            if (logTail.is(":checked")) {
                if (logTail.is(":visible")) {
                    that.getLogData(that.logLimit, -1);
                    var logContent = $("#log-content");
                    logContent.scrollTop(logContent[0].scrollHeight);
                }
                that.tailLog();
            }
        }, 1000);
    },

    setLogLinesVisibility: function() {
        $.each($(".log-level-filter"), function() {
            var selector = $("#log-content").find("p:has(> span." + $(this).attr("name") + ")");
            if ($(this).is(":checked")) {
                selector.show();
            } else {
                selector.hide();
            }
        });
    },

    getLogData: function(limit, offset) {
        var url = "api/log/simulator.log";
        url += "/" + (limit >= 0 ? limit : this.logLimit);
        if (offset >= 0) {
            url += "/" + offset;
        }
        var that = this;
        $.ajax({
            url: url,
            type: "GET",
            success: function(response) {
                var logContent = $("#log-content");
                logContent.empty();
                var regex = /(\d\d\d\d-\d\d-\d\dT\d\d:\d\d:\d\d.\d\d\d)\s+(\[([^\]]+)])\s+(.*)/;
                var logLine = $("<p></p>");
                $.each(response.lines, function(index, line) {
                    line = _.escape(line);
                    var matches = line.match(regex);
                    if (matches) {
                        if (logLine.html() !== "") {
                            logContent.append(logLine);
                        }
                        line = "<span class='log-time'>" + matches[1] + "</span> "; // add timestamp
                        line += "<span class='log-level " + matches[3].trim() + "'>" + matches[2].trim() + "</span> "; // add log level
                        line += "<span class='log-message'>" + matches[4] + "</span>"; // add log message
                        logLine = $("<p></p>");
                        logLine.html(line);
                    } else {
                        // don't know how to parse this line. it could be a continuation from last line. add it to the current logLine
                        var html = logLine.html();
                        if (html !== "") {
                            html += "<br />                                "; // TODO: align without using space, makes copy/paste easier
                        }
                        logLine.html(html + line);
                    }
                });
                logContent.append(logLine);
                that.setLogLinesVisibility();
                that.logTotal = response.total;
                that.logOffset = response.offset;
            },
            error: function(jqXhr, textStatus, errorThrown) {
                bootbox.alert("Kunne ikke hente loggfil for Troxy.<br/>Status: " + textStatus + "<br />Årsak: " + errorThrown);
            }
        });
    }
}));
