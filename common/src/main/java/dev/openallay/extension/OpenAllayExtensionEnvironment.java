package dev.openallay.extension;

/** Runtime compatibility coordinates used before an Extension is published. */
public record OpenAllayExtensionEnvironment(
        String loader,
        String minecraftVersion,
        String openAllayApiVersion) {
    public OpenAllayExtensionEnvironment {
        loader = require(loader, "loader").toLowerCase(java.util.Locale.ROOT);
        minecraftVersion = require(minecraftVersion, "minecraftVersion");
        openAllayApiVersion = require(openAllayApiVersion, "openAllayApiVersion");
    }

    public String incompatibility(OpenAllayExtensionDescriptor descriptor) {
        if (!descriptor.loaders().contains(loader)) {
            return "incompatible_loader";
        }
        if (!ExtensionCompatibility.includes(
                descriptor.minecraftVersionRange(), minecraftVersion)) {
            return "incompatible_game_version";
        }
        if (!ExtensionCompatibility.includes(
                descriptor.openAllayApiVersionRange(), openAllayApiVersion)) {
            return "incompatible_openallay_api";
        }
        return "";
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.strip();
    }
}
