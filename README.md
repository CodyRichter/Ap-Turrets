# AP-Turrets
## Overview
AP-Turrets is a plugin written for Spigot versions 1.10.2-1.12.2 with added integration for Movecraft 6 and Vault. The main feature of the plugin is adding mountable turrets which shoot projectiles to Minecraft, with configurable properties such as range, damage, rate of fire, and more.

## Usage
A user should create a sign with the following text:
Line 1: |  Mounted  |
Line 2: |    Gun    |
Line 3: |           |
Line 4: |           |

Then, when the sign is right-clicked, the player will be moved to the sign's location and immobilized. At this point, right-clicking with the trigger (default: stone button) in your main hand will shoot a projectile from the player's location in the direction they are facing. By holding right click, the projectile will be a fully automatic, firing projectiles until the trigger is released. Every time a projectile is fired, it will remove the required ammunition from the player's inventory (default: 1 arrow per shot)

In order to demount the turret, use the crouch key (default: Left Shift), which will let you regain movement and lose the ability to fire projectiles.

To get information on the turret itself such as damage or range, left click the turret sign while holding the trigger (default: stone button) in your hand.

If the turret is directly on a chest, ammunition will be drawn out of the chest first before the player's inventory is checked for ammunition. This allows for a type of "magazine", where any player with only the trigger item can use the mounted turret.
## Useful Integrations

The plugin integrates into other commonly used plugins to increase functionality:
### Vault
By hooking into the Vault economy system, it is possible to set a configurable price to create a turret which is removed from the player's account on turret creation.

### Movecraft 6
Movecraft 6 allows for large-scale vehicles to be built out of thousands of blocks, then moved around in the world (such as airships and boats). AP-Turrets integrates with the plugin, allowing for ammunition to be drawn from inventory containers on a piloted craft such as chests to provide a larger stockpile of ammunition for vessels with multiple players.
