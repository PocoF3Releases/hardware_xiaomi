# Xiaomi Dolby SELinux policy

Included by hardware/xiaomi/dolby/dolby.mk through BOARD_VENDOR_SEPOLICY_DIRS.
Keep DMS attributes, service/domain labels, audio/codec binder clients and Dolby
capability properties here; importing devices must not duplicate these types.
The existing DMS 2.0 rules and data labels were moved unchanged from sm8250-common.
Other DMS versions or implementations need their own verified integration.

vendor_init initializes vendor_dolby_config_prop from vendor/build.prop.
AudioFlinger and XiaomiDolby read it; the settings app cannot write it.
Without the setter permission, boot rejects dap.control and capability properties,
leaving AudioFlinger on control=none even when build.prop requests qdsp.
Legacy commented exported_system_prop mappings were removed from the device tree;
this change does not relabel the existing DAX support/version properties.

Validation: rule-preserving relocation, single-definition/include checks and
 git diff --check. No ROM/policy build or live policy injection performed.
After rebuilding both repositories together, reboot and verify the four capability
properties, absence of vendor_init set denials, DMS registration and DAP attachment
under playback. Bookkeeping acknowledgments are not DSP measurements.
