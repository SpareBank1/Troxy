var Troxy = {
    views: {},
    router: Backbone.Router.extend({
        initialize: function() {
            // set up routing
            // add "status" as default route
            this.route("*default", "defaultRoute", function() { Troxy.showView("status") });
            for (var name in Troxy.views) {
                if (!Troxy.views.hasOwnProperty(name)) {
                    continue;
                }
                var el = $("#view-" + name);
                Troxy.views[name] = new Troxy.views[name]({el: el});
                (function(router, name) {
                    var routeArgs = el.attr("data-route-args");
                    router.route(name + (routeArgs ? routeArgs : ""), name, function() { Troxy.showView(name); });
                })(this, name);
            }

            $("#troxy-address-host").text(location.protocol + "//" + location.hostname + (location.port ? ":" + location.port : ""));
            Troxy.waitForAsyncTasks(function() {
                Backbone.history.start();
            });
        }
    }),

    registerView: function(path, view) {
        var parenPos = path.indexOf("(");
        var slashPos = path.indexOf("/");
        var routeArgs;
        if (parenPos >= 0 || slashPos >= 0) {
            if (parenPos < 0) {
                routeArgs = path.substring(slashPos);
                path = path.substring(0, slashPos);
            } else if (slashPos < 0) {
                routeArgs = path.substring(parenPos);
                path = path.substring(0, parenPos);
            } else {
                var start = Math.min(slashPos, parenPos);
                routeArgs = path.substring(start);
                path = path.substring(0, start);
            }
        }
        this.views[path] = view;
        // add div for this view
        var mainContent = $("#main-content");
        var viewContent = $("<div></div>").attr("id", "view-" + path).attr("data-route-args", routeArgs).hide();
        mainContent.append(viewContent);
    },

    showView: function(name, args) {
        // hide all views
        $("#main-content").find("> div").each(function() {
            $(this).hide();
        });
        // show new view (resize fixes some rendering issues)
        $("#view-" + name).show().resize();
        if (this.views[name].onShow) {
            this.views[name].onShow.apply(this.views[name], args);
        }

        // highlight navigation menu item
        var menuItem = $("#main-menu-" + name).addClass("selected");
        if (menuItem.length) {
            $("#main-menu").find(".selected").removeClass("selected");
            menuItem.addClass("selected");
        }
    },

    waitForAsyncTasks: function(callback) {
        if (jQuery.active) {
            setTimeout(function() { Troxy.waitForAsyncTasks(callback); }, 50);
        } else {
            if (callback)
                callback();
        }
    }
};

$(document).ready(function() {
    // load views
    $(".main-menu-item").each(function() {
        var viewName = $(this).attr("href").substr(3);
        $.getScript("views/" + viewName + "/" + viewName + ".js");
    });

    // turn off ajax caching as it breaks everything in IE
    $.ajaxSetup({cache: false});

    // IE doesn't support "String.prototype.endsWith()", so let's create one
    String.prototype.endsWith = function(suffix) {
        return this.indexOf(suffix, this.length - suffix.length) !== -1;
    };

    Troxy.waitForAsyncTasks(function() { new Troxy.router(); });
});

