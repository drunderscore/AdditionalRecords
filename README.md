# AdditionalRecords
Allows for the creation of custom music discs with custom sounds.

Licensed under GNU GPL Version 3

## Install
Plop this jar into your plugins.

## Use
Grant the `additionalrecords.additionalrecords` permission to create custom records.

Use the command `/additionalrecords` (or `/arecords`) while holding a Music Disc as follows:  
`/arecords minecraft:entity.tnt.primed {"text":"top-tier comedy","color":"green"}`  
Will play the TNT Primed sound when inserted into a Jukebox, and display the title in the action bar.

`/arecords mycustomnamespace:music.summer_natures_crescendo {"text":"ConcernedApe - Summer (Nature's Crescendo)","color":"green"}`  
Similar to above, showing the ability to play custom sounds from resourcepacks.

## API
Use `AdditionalRecords#createRecordForItemStack` to modify an `ItemStack` to be useable
in a Jukebox with a custom sound, and optionally custom title.
