package io.quarkiverse.endive.deployment.items;

import io.quarkus.builder.item.EmptyBuildItem;

/**
 * An {@link EmptyBuildItem} subclass that marks the completion of a build step that loads all configured Wasm modules.
 */
public class WasmContextRegistrationCompleted extends EmptyBuildItem {
}
