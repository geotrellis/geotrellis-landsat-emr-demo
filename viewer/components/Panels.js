"use strict";
import React from 'react';
import _ from 'lodash';
import { PanelGroup, Panel, Input, Button, ButtonGroup, Form } from 'react-bootstrap';
import SingleLayer from "./SingleLayer";
import DiffLayer from "./DiffLayer";
import DiffLayers from "./DiffLayers";
import AverageByState from "./AverageByState";
import AverageDiffByState from "./AverageDiffByState";

var Panels = React.createClass({
  getInitialState: function () {
    return {
      activePane: 1,
      autoZoom: true
    };
  },
  handleNDI: function(ndi) {
    this.props.setIndexType(ndi);
  },
  handleAutoZoom: function(e) {
    let v = e.target.checked || false;
    this.setState(_.merge({}, this.state, {autoZoom: v}));
    if (v) this.props.showExtent(this.props.layers[this.state.layerId1].extent);
  },
  handlePaneSelect: function(id) {
    console.log("PANE SELECT %s", id);
    let newState = _.merge({}, this.state, { activePane: +id });
    this.setState(newState);
    if (id == 1) {
      this.props.setLayerType('singleLayer');
    } else if (id == 2) {
      this.props.setLayerType('intraLayerDiff');
    }
  },
  updateState: function(target, value) {
    let newState = _merge({}, this.state, {[target]: value});
    this.setState(newState);
  },
  showExtent: function(id) {
    var self = this;
    return function() {
      if (id == self.state.activePane && self.state.autoZoom) { // if the message is from active pane, pass it on
        self.props.showExtent.apply(this, arguments);
      }
    };
  },
  showLayer: function (id) {
    var self = this;
    return function() {
      if (id == self.state.activePane) { // if the message is from active pane, pass it on
        return self.props.showLayer.apply(self, arguments);
      } else {
        return null;
      }
    };
  },
  showLayerWithBreaks: function (id) {
    var self = this;
    return function() {
      if (id == self.state.activePane) { // if the message is from active pane, pass it on
        return self.props.showLayerWithBreaks.apply(self, arguments);
      } else {
        return null;
      }
    };
  },
  showStateAverage: function(id) {
    var self = this;
    return function() {
      if (id == self.state.activePane) { // if the message is from active pane, pass it on
        self.props.showStateAverage.apply(this, arguments);
      }
    };
  },
  showStateDiffAverage: function(id) {
    var self = this;
    return function() {
      if (id == self.state.activePane) { // if the message is from active pane, pass it on
        self.props.showStateDiffAverage.apply(this, arguments);
      }
    };
  },
  componentDidUpdate: function(prevProps, prevState) {
    // force map refresh if either the pane selection changed or auto-zoom was clicked
    // this must happen after state update in order for this.showLayerWithBreaks to pass the info
    if (this.state != prevState) {
      switch (this.state.activePane) {
      case 1:
        this.refs.single.updateMap();
        break;
      case 2:
        this.refs.diff.updateMap();
        break;
      case 3:
        this.refs.layerDiff.updateMap();
      case 4:
        this.refs.averageByState.updateMap();
      case 5:
        this.refs.averageDiffByState.updateMap();
      }
    }
  },
  render: function() {
    let nonLandsatLayers = _.filter(this.props.layers, l => {return ! l.isLandsat});
    let showNEXLayers = nonLandsatLayers.length > 0;
    return (
    <div>
      <Input type="checkbox" label="Snap to layer extent" checked={this.state.autoZoom} onChange={this.handleAutoZoom} />
      <ButtonGroup>
        <Button active={this.props.ndi == 'ndvi'} onClick={() => this.props.setIndexType('ndvi')}>NDVI</Button>
        <Button active={this.props.ndi == 'ndwi'} onClick={() => this.props.setIndexType('ndwi')}>NDWI</Button>
      </ButtonGroup>
      <PanelGroup defaultActiveKey="1" accordion={true} onSelect={this.handlePaneSelect}>
        <Panel header="Single Layer" eventKey="1" id={1}>
          <SingleLayer
            ref="single"
            rootUrl={this.props.rootUrl}
            layers={this.props.layers}
            activeLayerId={this.props.activeLayerId}
            showLayer={this.showLayer(1)}
            showLayerWithBreaks={this.showLayerWithBreaks(1)}
            showExtent={this.showExtent(1)}
            setLayerName={this.props.setLayerName}
            registerTime={this.props.registerTime}
          />
        </Panel>

        <Panel header="Change Detection" eventKey="2" id={2}>
          <DiffLayer
            ref="diff"
            rootUrl={this.props.rootUrl}
            layers={this.props.layers}
            showLayer={this.showLayer(2)}
            showLayerWithBreaks={this.showLayerWithBreaks(2)}
            showExtent={this.showExtent(2)}
            registerTime={this.props.registerTime}
          />
        </Panel>
      </PanelGroup>
    </div>)
  }
});

module.exports = Panels;
