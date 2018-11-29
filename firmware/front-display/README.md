# VFD main display driver - protocol specification

Display supports following modes:
* static text
* scrolled text
* graphic

## Static text

```
4|start|max length|zero-terminated utf8 text
```

|Value      |Size   |Description
|-----------|-------|---------------
|4          |1      |Magic byte
|start      |1      |Starting position of text. Between 0 and 39.
|max length |1      |Max length of the text. If the text is shorter, remaining place is filled with spaces.
|text       |*n*    |Zero-terminated UTF-8 text. Only Polish characters are supported. If text is longer than the display allows, it's truncated.


## Scrolling text

```
5|slot|start|length|zero terminated text
```

|Slot |Capacity |
|-----|---------|
|0    |150      |
|1    |70       |
|2    |30       |

## Pixel mode

todo: sum mode

```
p|start|length|sum with text|column bytes
```

|Value         |Size  |Description  |
|--------------|------|-------------|
|6             |1     |Magic byte
|start         |1     |Starting column
|length        |1     |Number of columns
|sum with text |1     |1 - when sum with existing content. 0 otherwise.
|column bytes  |*n*   |Content. Youngest bit is associated with uppermost pixel.
