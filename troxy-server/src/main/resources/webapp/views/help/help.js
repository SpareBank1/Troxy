Troxy.registerView("help", Backbone.View.extend({
    markdownParser: new showdown.Converter(),

    initialize: function() {
        var that = this;
        $.get("/views/help/help.html", function(html) {
            that.$el.html(html);
            // set up tab clicks
            var tabs = $(".help-tab");
            tabs.click(function() {
                tabs.removeClass("selected");
                $(this).addClass("selected");
                that.showHelpPage($(this).attr("id"));
            });

            // display about page
            that.showHelpPage("about-tab");
        });
    },

    onShow: function() {
    },

    showHelpPage: function(pageId) {
        var that = this;
        var page = pageId.substring(0, pageId.length - 4);
        $.get("/views/help/markdown/" + page + ".md", function(markdown) {
            var html = that.markdownParser.makeHtml(markdown);
            // replace "${troxy.url}" with proper value
            html = html.replace("${troxy.url}", window.location.host);

            $("#help-page").html(html);
        });
    }
}));
