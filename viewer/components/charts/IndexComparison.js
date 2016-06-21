import React from 'react';
import { render } from 'react-dom';
import shortid from 'shortid';
import _ from 'lodash';

var IndexComparison = React.createClass({
  _renderChart: function() {
    MG.data_graphic({
      target: document.getElementById(this.domId),
      data: [],
      title: this.props.poly.ndi + ' over time',
      width: this.props.width || 400,
      height: this.props.height || 200,
      right: this.props.rightOffset || 40,
      x_accessor: this.props.xAccessor || 'date',
      y_accessor: this.props.yAccessor || 'value'
    });
  },
  componentDidMount: function() {
    this._renderChart();
  },
  onComponentDidUpdate: function() {
    this._renderChart();
  },
  render: function() {
    if (! this.domId) { this.domId = shortid.generate(); }

    return (
      <div id={this.domId}></div>
    );
  }
});

module.exports = IndexComparison;
