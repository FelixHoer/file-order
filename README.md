# file-order

file-order is a Java/Clojure desktop application,
that allows to specify the order of files via drag-and-drop for systems, 
which rely on alphabetical order.

![Diagram](https://github.com/FelixHoer/file-order/raw/master/diagram.png)

This comes in handy when dealing with devices or applications, 
that depend on the alphabetical ordering of files, ie:

* slide show of pictures on a PC
* episodes of a series on the TV
* songs from a pendrive or CD on a Car radio

## Usage

Start the program by entering the following command in the `file-order` folder:

```
lein run
```

Select a directory, containing files to sort.

![Screenshot](https://github.com/FelixHoer/file-order/raw/master/screenshot.jpg)

Drag and drop items to their desired position.

Click `Order!` to enforce the desired succession. 
(a prefix will be added to achieve the same succession for alphabetical ordering)

## Requirements

* [Java](http://www.oracle.com/technetwork/java)
* [Clojure](http://clojure.org/)
* [Leiningen](http://leiningen.org/)

## Bundled Resources

* [Gnome Icons](http://www.gnome.org/)
  * audio-x-generic.png
  * image-x-generic.png
  * text-x-generic.png
  * video-x-generic.png

## License

file-order is MIT licensed. 
See the links above for licenses of external resources.
