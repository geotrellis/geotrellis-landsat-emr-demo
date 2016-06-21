import React from 'react';
import { render } from 'react-dom';
import fetch from 'isomorphic-fetch';
import shortid from 'shortid';
import _ from 'lodash';
import Loader from 'react-loader';
import MG from 'metrics-graphics';
import "metrics-graphics/dist/metricsgraphics.css";

function round(num) { return +(Math.round(num + "e+2")  + "e-2"); }

var TimeSeries = React.createClass({
  getInitialState: function () {
    return { loaded: false };
  },
  _fetchTimeSeries: function(pointLayer, ndi) {
    let root = pointLayer.chartProps.rootURL;
    let layerName = pointLayer.chartProps.layerName;
    let latlng = pointLayer._latlng;
    let url = `${root}/series/${layerName}/${ndi}?lat=${latlng.lat}&lng=${latlng.lng}`;

    return fetch(url).then( response => {
      response.json().then( summary => {
        var data = _.chain(summary.answer)
          .map(function(d) { return { "date": new Date(d[0]), "value": d[1] }; })
          .filter(function(d) { return _.isNull(d.value) ? false : true; })
          .value();

        pointLayer.stats[ndi] = data;
        this.setState({ loaded: true });
        this._renderChart(pointLayer, ndi);
      });
    },
    error => {
    });
  },
  _renderChart: function(point, ndi) {
    MG.data_graphic({
      target: document.getElementById(this.domId),
      data: point.stats[this.props.ndi],
      title: (ndi == 'ndvi' ? 'NDVI' : 'NDWI') + ` values at ${round(point._latlng.lat) + ', ' + round(point._latlng.lng) }`,
      width: this.props.width || 300,
      height: this.props.height || 200,
      right: this.props.rightOffset || 40,
      x_accessor: this.props.xAccessor || 'date',
      y_accessor: this.props.yAccessor || 'value'
    });
  },
  componentWillMount: function() {
    if (! this.props.point.stats[this.props.ndi]) {
      this.setState({ loaded: false });
      this._fetchTimeSeries(this.props.point, this.props.ndi);
    } else {
      this.setState({ loaded: true });
      this._renderChart(this.props.point, this.props.ndi)
    }
  },
  componentWillReceiveProps: function(nextProps) {
    if (! nextProps.point.stats[this.props.ndi]) {
      this.setState({ loaded: false });
      this._fetchTimeSeries(nextProps.point, nextProps.ndi);
    } else if (this.state.loaded) {
      this._renderChart(nextProps.point, nextProps.ndi);
    }
  },
  render: function() {
    this.domId = shortid.generate();
    return (
      <Loader loaded={this.state.loaded}>
        <div id={this.domId}></div>
      </Loader>
    );
  }
});

module.exports = TimeSeries;
