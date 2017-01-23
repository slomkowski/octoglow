# VFD main display driver - protocol specification

Display supports following modes:
* static text
* scrolled text
* graphic

## Static text

```
t|start|zero-terminated utf8 text
```

|Value  |Size   |Description
|-------|-------|---------------
|`t`    |1      |Magic byte
|start  |1      |Starting position of text. Between 0 and 39.
|text   |*n*    |Zero-terminated UTF-8 text. Only Polish characters are supported. If text is longer than the display allows, it's truncated.


## Scrolling text

```
s|slot|start|length|zero terminated text
```

|Slot |Capacity |
|-----|---------|
|0    |150      |
|1    |70       |
|2    |30       |

## Pixel mode

todo: sum mode

```
p|start|length|column bytes
```

p - override mode
P - sum mode