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

Validation: rule-preserving relocation and single-definition/include checks passed.
Later rebuilt-device checks on alioth confirmed all four capability properties,
no matching vendor_init property-set denials in the captured log, and acknowledged
DAP attachment/pregain during playback. The earlier control=none blocker is resolved
in that recorded build. Retain the paired sm8250-common policy removal when porting.
These control acknowledgments are not DSP measurements; recheck boot and playback
after changing policy or vendor binaries. See ../Readme.md for the validation scope.
