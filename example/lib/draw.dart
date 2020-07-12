import 'package:flutter/material.dart';

class Draw extends StatelessWidget {
  final List<dynamic> results;
  final int previewH;
  final int previewW;
  final double screenH;
  final double screenW;
  final bool isFrontFacing;

  const Draw({
    this.results,
    this.previewH,
    this.previewW,
    this.screenH,
    this.screenW,
    this.isFrontFacing,
  });

  @override
  Widget build(BuildContext context) {
    List<Widget> _renderKeypoints() {
      var lists = <Widget>[];
      // results.forEach((re) {
      var list = results.map<Widget>((k) {
        var _x = k["x"];
        var _y = k["y"];

        _x = _x / previewW * screenW;
        _y = _y / previewH * screenH;

        // To solve mirror problem on front camera
        if (isFrontFacing) {
          if (_x > screenW / 2) {
            var temp = _x - (screenW / 2);
            _x = (screenW / 2) - temp;
          } else {
            var temp = (screenW / 2) - _x;
            _x = (screenW / 2) + temp;
          }
        }

        return Positioned(
          left: k["score"] > 0.3 ? double.parse(_x.toString()) : -1000,
          top: k["score"] > 0.3 ? double.parse(_y.toString()) : -1000,
          width: 10,
          height: 15,
          child: Container(
            child: Text(
              "●", //${k["score"]}",
              style: TextStyle(
                color: Colors.red,
                fontSize: 18.0,
              ),
            ),
          ),
        );
      }).toList();

      lists..addAll(list);
      return lists;
    }

    return Stack(
      children: _renderKeypoints(),
    );
  }
}
