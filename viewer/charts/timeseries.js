import MG from "metrics-graphics";
import "metrics-graphics/dist/metricsgraphics.css";

var timeSeries = function(targetElemId, data, title, width, height, rightOffset, xAccessor, yAccessor) {
  //var updatedData = MG.convert.date(data, 'date');
  MG.data_graphic({
    target: document.getElementById(targetElemId),
    data: data,
    title: title || "",
    width: width || 400,
    height: height || 200,
    right: rightOffset || 40,
    x_accessor: xAccessor || 'date',
    y_accessor: yAccessor || 'value'
  });
};

module.exports = timeSeries;

