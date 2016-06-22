import React from 'react';
import { render } from 'react-dom';
import shortid from 'shortid';
import _ from 'lodash';
import Loader from 'react-loader';

var IndexComparison = React.createClass({
  getInitialState: function () {
    return { loaded: false };
  },
  _fetchPolygonalSummary: function(polyLayer, ndi) {
    let root = polyLayer.chartProps.rootURL;
    let layerName = polyLayer.chartProps.layerName;
    let latlng = polyLayer._latlng;
    let timeQString = `?time=${this.props.times[0]}`;
    let otherTimeQString = (this.props.layerType == 'intraLayerDiff' ? `&otherTime=${this.props.times[1]}` : '');
    let url = `${root}/mean/${layerName}/${ndi}` + timeQString + otherTimeQString;

    return fetch(url, {
      method: 'POST',
      body: JSON.stringify(polyLayer.toGeoJSON().geometry)
    }).then( response => {
      response.json().then( summary => {
        var data = summary.answer;

        polyLayer.stats[ndi] = data;
        this.setState({ loaded: true });
        this._renderChart(polyLayer, ndi);
      });
    },
    error => {});
  },
  _fillBox: function(ctx, value, ndi) {
    let color = ndi === 'ndvi' ? '#64c59d' : '#add8e6';
    ctx.fillStyle = color;
    if (value > 0) {
      ctx.fillRect(
        150,
        50,
        value * 150,
        130
      );
    } else {
      ctx.fillRect(
        150 + (value * 150),
        50,
        Math.abs(value) * 150,
        130
      );
    }
  },
  _renderChart: function(polyLayer, ndi) {
    let canvas = {
      width: 300,
      height: 200
    };
    const ctx = this.refs.canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    this._fillBox(ctx, polyLayer.stats[this.props.ndi], ndi);
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
    //MG.data_graphic({
    //  target: document.getElementById(this.domId),
    //  title: "Bar Prototype",
    //  bar_orientation: 'vertical',
    //  data: polyLayer.stats[this.props.ndi],
    //  chart_type: 'bar',
    //  y_accessor: 'value',
    //  min_y: -1.0,
    //  max_y: 1.0,
    //  height: (this.props.height || 200),
    //  full_width: true,
    //  right: (this.props.rightOffset || 40)
    //});
  },
  componentWillMount: function() {
    if (! this.props.poly.stats[this.props.ndi]) {
      this.setState({ loaded: false });
      this._fetchPolygonalSummary(this.props.poly, this.props.ndi);
    } else {
      this.setState({ loaded: true });
      this._renderChart(this.props.poly, this.props.ndi);
    }
  },
  componentWillReceiveProps: function(nextProps) {
    if (! nextProps.poly.stats[nextProps.ndi]) {
      this.setState({ loaded: false });
      this._fetchPolygonalSummary(nextProps.poly, nextProps.ndi);
    } else if (this.state.loaded) {
      this._renderChart(nextProps.poly, nextProps.ndi);
    }
  },
  render: function() {
    this.domId = shortid.generate();
    if (! this.domId) { this.domId = shortid.generate(); }

    return (
      <Loader loaded={this.state.loaded}>
        <div id={this.domId}>
          <canvas ref="canvas" width={300} height={200} />
        </div>
      </Loader>
    );
  }
});

module.exports = IndexComparison;
