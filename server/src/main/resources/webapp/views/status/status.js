Troxy.registerView("status", Backbone.View.extend({
    events: {
        "change select#troxy-mode-dropdown": "changeTroxyMode",
        "change select#statistics-interval-dropdown": "changeStatisticsInterval"
    },

    initialize: function() {
        var that = this;
        $.get("/views/status/status.html", function(html) {
            that.$el.html(html);
            setInterval(that.updateStatus, 10000);
        });
    },

    onShow: function() {
        this.updateStatus();
    },

    updateStatus: function() {
        $.ajax({
            url: "api/status",
            type: "GET",
            success: function(response) {
                $("#main-logo-img").attr("src", "/images/logo/logo_" + response.mode + ".png");
                $("#mode-indicator-img").attr("src", "/images/modeindicator/mode_" + response.mode + ".png");
                $("#status-image").attr("src", "/views/status/images/status_" + response.mode + ".png");
                $("#troxy-mode-dropdown").val(response.mode);
                $("#statistics-interval-dropdown").val(response.statisticsInterval);
                $("#troxy-version-info").text("Troxy-versjon: " + response.version + " (" + response.release + ").");
                var lastActivity = $("#last-activity");
                if (!$.isEmptyObject(response.activity)) {
                    lastActivity.show();
                    var lastActivityList = $("#last-activity-list");
                    lastActivityList.empty();
                    $.each(response.activity, function(address, time) {
                        var addressSpan = $("<span></span>").addClass("address").text(address);
                        var timeSpan = $("<span></span>").addClass("time").text(new Date(time));
                        var li = $("<li></li>").append(addressSpan).append(timeSpan);
                        lastActivityList.append(li);
                    });
                } else {
                    lastActivity.hide();
                }
            }
        });
    },

    changeTroxyMode: function() {
        var troxyMode = $("#troxy-mode-dropdown").find("option:selected")[0].value;
        var that = this;
        $.ajax({
            url: "api/status/mode",
            type: "PUT",
            data: troxyMode,
            success: function() {
                that.updateStatus();
            },
            error: function(jqXhr, textStatus, errorThrown) {
                bootbox.alert("Kunne ikke endre modus for Troxy.<br/>Status: " + textStatus + "<br />Årsak: " + errorThrown);
            }
        });
    },

    changeStatisticsInterval: function() {
        var statisticsInterval = $("#statistics-interval-dropdown").find("option:selected")[0].value;
        var that = this;
        $.ajax({
            url: "api/status/statisticsInterval",
            type: "PUT",
            data: statisticsInterval,
            success: function() {
                that.updateStatus();
            },
            error: function(jqXhr, textStatus, errorThrown) {
                bootbox.alert("Kunne ikke endre statistikkintervall for Troxy.<br/>Status: " + textStatus + "<br />Årsak: " + errorThrown);
            }
        });
    }
}));
