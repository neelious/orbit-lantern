using MediaBrowser.Common.Configuration;
using MediaBrowser.Common.Plugins;
using MediaBrowser.Model.Plugins;
using MediaBrowser.Model.Serialization;

namespace Lantern;

/// <summary>Serves an alternative web interface from the server at /lantern.</summary>
public class Plugin : BasePlugin<PluginConfiguration>
{
    public Plugin(IApplicationPaths applicationPaths, IXmlSerializer xmlSerializer)
        : base(applicationPaths, xmlSerializer)
    {
        Instance = this;
    }

    public static Plugin? Instance { get; private set; }

    public override string Name => "Orbit Lantern";

    public override Guid Id => Guid.Parse("fd906ae7-849f-4160-825f-f753a3571ccc");

    public override string Description => "Alternative web interface. Open /lantern on this server.";
}

public class PluginConfiguration : BasePluginConfiguration
{
}
