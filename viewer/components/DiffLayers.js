"use strict";
import React from 'react';
import _ from 'lodash';
import { PanelGroup, Panel, Input, Button, ButtonGroup } from 'react-bootstrap';
import ifAllDefined from '../utils/utils.js';

function updateInterLayerDiffMap (showLayerWithBreaks, showMaxState, hideMaxState, showMaxAverageState, hideMaxAverageState, root, layer1, layer2, t1, t2, showingMaxState, showingMaxAverageState) {
  let time1 = layer1.times[t1];
  let time2 = layer2.times[t2];
  // Showing second layer as a stand-in for now
  showLayerWithBreaks(
    `${root}/layerdiff/${layer1.name}/${layer2.name}/{z}/{x}/{y}?time1=${time1}&time2=${time2}`,
    `${root}/layerdiff/breaks/${layer1.name}/${layer2.name}?time1=${time1}&time2=${time2}`
  );

  if(showingMaxState) {
    showMaxState(`${root}/maxstate/${layer1.name}/${layer2.name}?time1=${time1}&time2=${time2}`);
  } else {
    hideMaxState();
  }

  if(showingMaxAverageState) {
    showMaxAverageState(`${root}/maxaveragestate/${layer1.name}/${layer2.name}?time1=${time1}&time2=${time2}`);
  } else {
    hideMaxState();
  }


  // showLayerWithBreaks(
  //   `${root}/layer-diff/${layer1.name}/{z}/{x}/{y}?layer2={$layer2.name}&time1=${time1}&time2=${time2}`,
  //   `${root}/layer-diff/breaks/${layer1.name}?layer2={$layer2.name}&time1=${time1}&time2=${time2}`
  // );
};

var MapViews = React.createClass({
  getInitialState: function () {
    return {
      layerId1: 1,
      layerId1time: 0,
      layerId2: 0,
      layerId2time: 0,
      showingMaxState: false,
      showingMaxAverageState: false,
      times: {}
    };
  },
  handleLayerSelect: function(ev, target) {
    let layerId = +ev.target.value;
    let newState = _.merge({}, this.state, {
      [target]: layerId,
      [target + "time"]: _.get(this.state.times[layerId], target + "time", undefined),
      "times": { // Saves time selection when switching layer
        [this.state.layerId]: {
          [target + "time"]: this.state[target + "time"]
        }
      }
    });

    this.setState(newState);
    this.updateMap(newState);
    this.props.showExtent(this.props.layers[layerId].extent);
  },
  handleShowMaxStateChecked: function(e) {
    let v = e.target.checked || false;
    let newState = _.merge({}, this.state, {showingMaxState: v});
    this.setState(newState);
    this.updateMap(newState);
  },
  handleShowMaxAverageStateChecked: function(e) {
    let v = e.target.checked || false;
    let newState = _.merge({}, this.state, {showingMaxAverageState: v});
    this.setState(newState);
    this.updateMap(newState);
  },
  updateState: function(target, value) {
    let newState = _.merge({}, this.state, {[target]: value});
    this.setState(newState);
    this.updateMap(newState);
  },
  updateMap: function (state) {
    if (! state) { state = this.state; }

    ifAllDefined(
      this.props.showLayerWithBreaks,
      this.props.showMaxState,
      this.props.hideMaxState,
      this.props.showMaxAverageState,
      this.props.hideMaxAverageState,
      this.props.rootUrl,
      this.props.layers[state.layerId1],
      this.props.layers[state.layerId2],
      state.layerId1time,
      state.layerId2time)(function(showLayerWithBreaks, showMaxState, hideMaxState, showMaxAverageState, hideMaxAverageState, rootUrl, layer1, layer2, t1, t2) {
        updateInterLayerDiffMap(showLayerWithBreaks, showMaxState, hideMaxState, showMaxAverageState, hideMaxAverageState, rootUrl, layer1, layer2, t1, t2,
                                state.showingMaxState,
                                state.showingMaxAverageState);
      });

    this.props.showExtent(this.props.layers[state.layerId1].extent);
  },
  render: function() {
    let layer1       = this.props.layers[this.state.layerId1];
    let layer2       = this.props.layers[this.state.layerId2];

    let layerOptions =
      _.map(this.props.layers, (layer, index) => {
        return <option value={index} key={index}>{layer.name}</option>;
      });

    let layer1Times =
      _.map(_.get(layer1, "times", []), (time, index) => {
        return <option value={index} key={index}>{time}</option>;
      });

    let layer2Times =
      _.map(_.get(layer2, "times", []), (time, index) => {
        return <option value={index} key={index}>{time}</option>;
      });

    let showMaxState = false;
    let showMaxAverageState = true;

    return (
      <div>
        <Input type="select" label="Layer A" placeholder="select" value={this.state.layerId1}
          onChange={e => this.handleLayerSelect(e, "layerId1")}>
          {layerOptions}
        </Input>

        <Input type="select" label="Time A" placeholder="select" value={this.state.layerId1time}
            onChange={e => this.updateState("layerId1time", +e.target.value)}>
          {layer1Times}
        </Input>

        <Input type="select" label="Layer B" placeholder="select" value={this.state.layerId2}
          onChange={e => this.handleLayerSelect(e, "layerId2")}>
          {layerOptions}
        </Input>

        <Input type="select" label="Time B" placeholder="select" value={this.state.layerId2time}
            onChange={e => this.updateState("layerId2time", +e.target.value)}>
          {layer2Times}
        </Input>
        { showMaxState ?
          <Input type="checkbox" label="Show state with max difference" checked={this.state.showingMaxState} onChange={this.handleShowMaxStateChecked} visibility="hidden"/>
          : null
        }
        { showMaxAverageState ?
          <Input type="checkbox" label="Show state with max average difference" checked={this.state.showingMaxAverageState} onChange={this.handleShowMaxAverageStateChecked} />
          : null
        }
      </div>
    )
  }
});

module.exports = MapViews;
