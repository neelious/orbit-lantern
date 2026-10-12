using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace Lantern;

/// <summary>Returns the bundled single-page app. Sign-in happens inside the page against the normal Jellyfin API.</summary>
[ApiController]
[Route("lantern")]
public class LanternController : ControllerBase
{
    private const string ResourceName = "Lantern.index.html";

    [HttpGet]
    [HttpGet("index.html")]
    [AllowAnonymous]
    [Produces("text/html")]
    public IActionResult GetApp()
    {
        var stream = typeof(LanternController).Assembly.GetManifestResourceStream(ResourceName);
        if (stream is null)
        {
            return NotFound();
        }

        Response.Headers.CacheControl = "no-cache";
        return File(stream, "text/html; charset=utf-8");
    }
}
