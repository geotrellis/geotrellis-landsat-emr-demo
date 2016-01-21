"use strict";
import React from 'react';
import _ from 'lodash';
import { PanelGroup, Panel, Input, Button, ButtonGroup } from 'react-bootstrap';
import ifAllDefined from "../utils/utils";

let usStates = ["Alabama","Arizona","Arkansas","California","Colorado","Connecticut","Delaware","Florida","Georgia","Idaho","Illinois","Indiana","Iowa","Kansas","Kentucky","Louisiana","Maine","Maryland","Massachusetts","Michigan","Minnesota","Mississippi","Missouri","Montana","Nebraska","Nevada","New Hampshire","New Jersey","New Mexico","New York","North Carolina","North Dakota","Ohio","Oklahoma","Oregon","Pennsylvania","Rhode Island","South Carolina","South Dakota","Tennessee","Texas","Utah","Vermont","Virginia","Washington","West Virginia","Wisconsin","Wyoming"];

function updateAverageDiffByStateMap (showLayerWithBreaks, showStateDiffAverage, root, usState, layer1, t1, layer2, t2) {
  // Single Band Calculation
  let time1 = layer1.times[t1];
  let time2 = layer2.times[t2];
  showLayerWithBreaks(
    `${root}/statediff/tiles/${usState}/${layer1.name}/${layer2.name}/{z}/{x}/{y}?time1=${time1}&time2=${time2}`,
    `${root}/layerdiff/breaks/${layer1.name}/${layer2.name}?time1=${time1}&time2=${time2}`
  );

  showStateDiffAverage(`${root}/statediff/average/${usState}/${layer1.name}/${layer2.name}?time1=${time1}&time2=${time2}`);
};

var AverageByState = React.createClass({
  getInitialState: function () {
    return {
      layerId1: 1,
      layerId1time: 0,
      layerId2: 0,
      layerId2time: 0,
      times: {},
      usStateId: 0
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
  updateState: function(target, value) {
    let newState = _.merge({}, this.state, {[target]: value});
    this.setState(newState);
    this.updateMap(newState);
  },
  updateMap: function (state) {
    if (! state) { state = this.state; }
    ifAllDefined(this.props.showLayerWithBreaks,
                 this.props.showStateDiffAverage,
                 this.props.rootUrl,
                 usStates[state.usStateId],
                 this.props.layers[state.layerId1],
                 state.layerId1time,
                 this.props.layers[state.layerId2],
                 state.layerId2time)
      (updateAverageDiffByStateMap);
    this.props.showExtent(this.props.layers[state.layerId].extent);
  },
  componentWillReceiveProps: function (nextProps){
  /** Use this as an opportunity to react to a prop transition before render() is called by updating the state using this.setState().
    * The old props can be accessed via this.props. Calling this.setState() within this function will not trigger an additional render. */
    if ( _.isUndefined(this.state.layerId1) && _.isUdefined(this.state.layerId2) && ! _.isEmpty(nextProps.layers)) {
      // we are blank and now is our chance to choose a layer and some times
      let newState = _.merge({}, this.state, { layerId1: 1, timeId1: 0, layerId2: 0, timeId2: 0 });
      this.setState(newState);
      var layer1 = nextProps.layers[0];
      var layer2 = nextProps.layers[1];
      updateAverageDiffByStateMap(nextProps.showLayerWithBreaks, this.props.showStateDiffAverage, nextProps.rootUrl, usStates[this.state.usStateId], layer1, 0, layer2, 0);
      nextProps.showExtent(layer1.extent);
    }
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

    let stateOptions =
      _.map(usStates, (name, index) => {
        return <option value={index} key={index}>{name}</option>;
      });

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

        <Input type="select" label="State" placeholder="select" value={this.state.usStateId}
            onChange={e => this.updateState("usStateId", + e.target.value)}>
          {stateOptions}
        </Input>
      </div>
    )
  }
});

module.exports = AverageByState;
