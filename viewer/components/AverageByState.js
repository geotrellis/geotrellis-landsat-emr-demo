"use strict";
import React from 'react';
import _ from 'lodash';
import { PanelGroup, Panel, Input, Button, ButtonGroup } from 'react-bootstrap';
import ifAllDefined from "../utils/utils";

let usStates = ["Alabama","Arizona","Arkansas","California","Colorado","Connecticut","Delaware","Florida","Georgia","Idaho","Illinois","Indiana","Iowa","Kansas","Kentucky","Louisiana","Maine","Maryland","Massachusetts","Michigan","Minnesota","Mississippi","Missouri","Montana","Nebraska","Nevada","New Hampshire","New Jersey","New Mexico","New York","North Carolina","North Dakota","Ohio","Oklahoma","Oregon","Pennsylvania","Rhode Island","South Carolina","South Dakota","Tennessee","Texas","Utah","Vermont","Virginia","Washington","West Virginia","Wisconsin","Wyoming"];

function updateAverageByStateMap (showLayerWithBreaks, showStateAverage, root, usState, layer, t1) {
  // Single Band Calculation
  let time1 = layer.times[t1];
  showLayerWithBreaks(
    `${root}/state/tiles/${usState}/${layer.name}/{z}/{x}/{y}?time=${time1}`,
    `${root}/tiles/breaks/${layer.name}?time=${time1}`
  );

  console.log("calling show average by state");
  showStateAverage(`${root}/state/average/${usState}/${layer.name}?time=${time1}`);
};

var AverageByState = React.createClass({
  getInitialState: function () {
    return {
      operation: "none",
      layerId: undefined, // layer index
      timeId: undefined,  // time index in layer
      times: {},           // maps from layerId => {timeId1 , timeId2}
      usStateId: 0
    };
  },
  handleLayerSelect: function(ev) {
    let layerId = +ev.target.value;
    let newState = _.merge({}, this.state, {
      "layerId": layerId,
      "time": _.get(this.state.times[layerId], "time", undefined),
      "times": { // Saves time selectio when switching layer
        [this.state.layerId]: {
          "time": this.state.time
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
    ifAllDefined(this.props.showLayerWithBreaks, this.props.showStateAverage, this.props.rootUrl, usStates[state.usStateId], this.props.layers[state.layerId], state.timeId)
      (updateAverageByStateMap);
    this.props.showExtent(this.props.layers[state.layerId].extent);
  },
  componentWillReceiveProps: function (nextProps){
  /** Use this as an opportunity to react to a prop transition before render() is called by updating the state using this.setState().
    * The old props can be accessed via this.props. Calling this.setState() within this function will not trigger an additional render. */
    if ( _.isUndefined(this.state.layerId) && ! _.isEmpty(nextProps.layers)) {
      // we are blank and now is our chance to choose a layer and some times
      let newState = _.merge({}, this.state, { layerId: 0, timeId: 0 });
      this.setState(newState);
      var layer = nextProps.layers[0];
      updateAverageByStateMap(nextProps.showLayerWithBreaks, nextProps.rootUrl, usStates[this.state.usStateId], layer, 0);
      nextProps.showExtent(layer.extent);
    }
  },
  render: function() {
    let layer       = this.props.layers[this.state.layerId];
    let isLandsat   = _.get(layer, "isLandsat", false);

    let layerOptions =
      _.map(this.props.layers, (layer, index) => {
        return <option value={index} key={index}>{layer.name}</option>;
      });

    let layerTimes =
      _.map(_.get(layer, "times", []), (time, index) => {
        return <option value={index} key={index}>{time}</option>;
      });

    let stateOptions =
      _.map(usStates, (name, index) => {
        return <option value={index} key={index}>{name}</option>;
      });

    return (
      <div>
        <Input type="select" label="Layer" placeholder="select" value={this.state.layerId}
          onChange={e => this.handleLayerSelect(e)}>
          {layerOptions}
        </Input>

        <Input type="select" label="Time" placeholder="select" value={this.state.timeId}
            onChange={e => this.updateState("timeId", +e.target.value)}>
          {layerTimes}
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
