using System.Text;
using MediaBrowser.Controller;
using MediaBrowser.Controller.Plugins;
using Microsoft.AspNetCore.Builder;
using Microsoft.AspNetCore.Hosting;
using Microsoft.AspNetCore.Http;
using Microsoft.Extensions.DependencyInjection;

namespace Lantern;

/// <summary>Hooks a small middleware into the server so the stock web client (and the apps that load it) boot into Lantern.</summary>
public class ServiceRegistrator : IPluginServiceRegistrator
{
    public void RegisterServices(IServiceCollection serviceCollection, IServerApplicationHost applicationHost)
    {
        serviceCollection.AddTransient<IStartupFilter, WebInjectionStartupFilter>();
    }
}

public class WebInjectionStartupFilter : IStartupFilter
{
    public Action<IApplicationBuilder> Configure(Action<IApplicationBuilder> next)
    {
        return app =>
        {
            app.UseMiddleware<WebInjectionMiddleware>();
            next(app);
        };
    }
}

/// <summary>Adds &lt;script src=".../lantern/boot.js"&gt; to the top of the web client's index.html.</summary>
public class WebInjectionMiddleware
{
    private const string Marker = "lantern/boot.js";
    private readonly RequestDelegate _next;

    public WebInjectionMiddleware(RequestDelegate next)
    {
        _next = next;
    }

    public async Task InvokeAsync(HttpContext context)
    {
        var path = context.Request.Path.Value ?? string.Empty;
        var webRoot = WebRoot(path);
        if (!HttpMethods.IsGet(context.Request.Method) || webRoot is null)
        {
            await _next(context);
            return;
        }

        // Always fetch a full, uncompressed page so it can be edited.
        context.Request.Headers.Remove("Accept-Encoding");
        context.Request.Headers.Remove("If-None-Match");
        context.Request.Headers.Remove("If-Modified-Since");

        var original = context.Response.Body;
        await using var buffer = new MemoryStream();
        context.Response.Body = buffer;
        try
        {
            await _next(context);
        }
        finally
        {
            context.Response.Body = original;
        }

        buffer.Position = 0;
        var isHtml = (context.Response.ContentType ?? string.Empty).Contains("text/html", StringComparison.OrdinalIgnoreCase);
        if (context.Response.StatusCode != StatusCodes.Status200OK || !isHtml)
        {
            await buffer.CopyToAsync(original);
            return;
        }

        var html = Encoding.UTF8.GetString(buffer.ToArray());
        if (!html.Contains(Marker, StringComparison.Ordinal))
        {
            var tag = $"<script src=\"{webRoot}/lantern/boot.js\"></script>";
            var head = html.IndexOf("<head>", StringComparison.OrdinalIgnoreCase);
            html = head >= 0 ? html.Insert(head + "<head>".Length, tag) : tag + html;
        }

        var bytes = Encoding.UTF8.GetBytes(html);
        context.Response.Headers.Remove("ETag");
        context.Response.Headers.CacheControl = "no-cache";
        context.Response.ContentLength = bytes.Length;
        await original.WriteAsync(bytes);
    }

    /// <summary>Returns the server base path (e.g. "" or "/jellyfin") when the request is for the web client's index page.</summary>
    private static string? WebRoot(string path)
    {
        foreach (var suffix in new[] { "/web/index.html", "/web/", "/web" })
        {
            if (path.EndsWith(suffix, StringComparison.OrdinalIgnoreCase))
            {
                return path[..^suffix.Length];
            }
        }

        return null;
    }
}
