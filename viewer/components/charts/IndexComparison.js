import React from 'react';
import { render } from 'react-dom';
import shortid from 'shortid';
import _ from 'lodash';
import Loader from 'react-loader';

var IndexComparison = React.createClass({
  getInitialState: function () {
    return { loaded: false };
  },
  _fetchPolygonalSummary: function(polyLayer, ndi, times, layerType) {
    let root = polyLayer.chartProps.rootURL;
    let layerName = polyLayer.chartProps.layerName;
    let latlng = polyLayer._latlng;
    let timeQString = `?time=${this.props.times[0]}`;
    let otherTimeQString = (layerType == 'intraLayerDiff' ? `&otherTime=${this.props.times[1]}` : '');
    let url = `${root}/mean/${layerName}/${ndi}` + timeQString + otherTimeQString;

    return fetch(url, {
      method: 'POST',
      body: JSON.stringify(polyLayer.toGeoJSON().geometry)
    }).then( response => {
      response.json().then( summary => {
        var data = summary.answer;

        if (layerType == 'intraLayerDiff') {
          polyLayer.comparisonStats[ndi] = data;
        } else {
          polyLayer.stats[ndi] = data;
        }
        this.setState({ loaded: true });
        this._renderChart(polyLayer, ndi, layerType);
      });
    },
    error => {});
  },
  _fillBox: function(ctx, value, ndi) {
    let color = ndi === 'ndvi' ? '#64c59d' : '#add8e6';
    ctx.fillStyle = color;
    ctx.fillRect(
      (value > 0 ? 150 : 150 + (value * 150)),
      50,
      Math.abs(value) * 150,
      130
    );
  },
  _renderChart: function(polyLayer, ndi, layerType) {
    let ctx = document.getElementById("canvas").getContext('2d');
    let canvas = {
      width: 300,
      height: 200
    };
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    if (layerType == 'intraLayerDiff') {
      this._fillBox(ctx, polyLayer.comparisonStats[ndi], ndi);
    } else {
      this._fillBox(ctx, polyLayer.stats[ndi], ndi);
    }
    ctx.fillStyle = '#000000';
    ctx.font = '15px Arial';

    // Index bottom
    ctx.textAlign = 'start';
    ctx.fillText('-1', 5, 20);
    ctx.beginPath();
    ctx.moveTo(0, 40);
    ctx.lineTo(0, canvas.height);
    ctx.stroke();

    // Index middle
    ctx.textAlign = 'center';
    ctx.fillText('0', 150, 20);
    ctx.beginPath();
    ctx.moveTo(150, 40);
    ctx.lineTo(150, canvas.height);
    ctx.stroke();

    // Index top
    ctx.textAlign = 'right';
    ctx.fillText('1', 295, 20);
    ctx.beginPath();
    ctx.moveTo(300, 40);
    ctx.lineTo(300, canvas.height);
    ctx.stroke();
  },
  componentDidMount: function() {
    if (this.props.layerType === 'intraLayerDiff') {
      if (! this.props.poly.comparisonStats[this.props.ndi]) {
        this.setState({ loaded: false });
        this._fetchPolygonalSummary(this.props.poly, this.props.ndi, this.props.times, this.props.layerType);
      } else {
        this.setState({ loaded: true });
        this._renderChart(this.props.poly, this.props.ndi, this.props.layerType);
      }
    } else {
      if (! this.props.poly.stats[this.props.ndi]) {
        this.setState({ loaded: false });
        this._fetchPolygonalSummary(this.props.poly, this.props.ndi, this.props.times, this.props.layerType);
      } else {
        this.setState({ loaded: true });
        this._renderChart(this.props.poly, this.props.ndi, this.props.layerType);
      }
    }
  },
  componentWillReceiveProps: function(nextProps) {
    if (nextProps.layerType === 'intraLayerDiff') {
      if (! nextProps.poly.comparisonStats[nextProps.ndi]) {
        this.setState({ loaded: false });
        this._fetchPolygonalSummary(nextProps.poly, nextProps.ndi, nextProps.times, nextProps.layerType);
      } else if (this.state.loaded) {
        this._renderChart(nextProps.poly, nextProps.ndi, nextProps.layerType);
      }
    } else {
      if (! nextProps.poly.stats[nextProps.ndi]) {
        this.setState({ loaded: false });
        this._fetchPolygonalSummary(nextProps.poly, nextProps.ndi, nextProps.times, nextProps.layerType);
      } else if (this.state.loaded) {
        this._renderChart(nextProps.poly, nextProps.ndi, nextProps.layerType);
      }
    }
  },
  render: function() {
    let loading = this.state.loaded ? null : (<p>Loading data...</p>)
    return (
      <div>
        {loading}
        <canvas id="canvas" width={300} height={200} hidden={! this.state.loaded}/>
      </div>
    );
  }
});

module.exports = IndexComparison;
