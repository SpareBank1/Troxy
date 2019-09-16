Troxy.registerView("statistics", Backbone.View.extend({
    events: {
        "change select#statistics-file-dropdown": "getStatisticsData"
    },

    initialize: function() {
        var that = this;
        $.get("/views/statistics/statistics.html", function(html) {
            that.$el.html(html);

            // set up statistics table
            $("#statistics-table").dataTable({
                "paging": false,
                "sScrollY": "auto",
                "scrollCollapse": true,
                "columns": [
                    {"width": "60%"},
                    {"width": "20%"},
                    {"width": "20%"}
                ],
                "language": {
                    "decimal": ",",
                    "thousands": ".",
                    "search": "Søk",
                    "info": "Viser _START_ til _END_ av _TOTAL_ opptaksfiler"
                }
            });

            $("#refresh-statistics").click(function(event) {
                that.updateStatisticsList();
                event.stopPropagation();
            });

            that.updateStatisticsList();
        });
    },

    onShow: function() {
        this.updateStatisticsList();
    },

    updateStatisticsList: function() {
        var dropdown = $("#statistics-file-dropdown");
        var selected = dropdown.find("option:selected").text();
        var updateTable = selected === "";
        var that = this;
        $.ajax({
            url: "api/statistics",
            type: "GET",
            success: function(response) {
                dropdown.empty();
                dropdown.append($("<option></option>").attr("value", "current").text("Inneværende"));
                $.each(response.sort().reverse(), function(index, value) {
                    var option = $("<option></option>").attr("value", value).text(value);
                    if (selected === value) {
                        option.attr("selected", "true");
                        updateTable = false;
                    }
                    dropdown.append(option);
                });
                if (updateTable) {
                    that.getStatisticsData();
                }
            },
            error: function(jqXhr, textStatus, errorThrown) {
                bootbox.alert("Kunne ikke hente statistikkfiler for Troxy.<br/>Status: " + textStatus + "<br />Årsak: " + errorThrown);
            }
        });
    },

    getStatisticsData: function() {
        var selected = $("#statistics-file-dropdown").find("option:selected").attr("value");
        $.get("api/statistics/" + selected, function(response) {
            var statisticsTable = $('#statistics-table');
            var datatable = statisticsTable.DataTable();
            datatable.clear();
            if (typeof(response) == "string") {
                var lines = response.split("\n");
                lines.forEach(function(line) {
                    if (line.trim() && line.charAt(0) != '#') {
                        datatable.row.add(line.split(","));
                    }
                });
            }
            datatable.draw();
        });
    }
}));
