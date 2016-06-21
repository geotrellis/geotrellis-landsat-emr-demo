import React, { Component, PropTypes } from 'react';
import { bindActionCreators } from 'redux';
import { connect } from 'react-redux';
import * as actions from '../redux/actions';
import Leaflet from '../components/Leaflet';
import Catalog from '../components/Catalog';
import Panels from '../components/Panels';
import _ from 'lodash';

import "bootstrap-webpack";

var App = React.createClass({

  render: function() {
    return (
    <div className="row">
        <div className="col-md-9">
          <Leaflet
            layerType={this.props.layerType}
            ndi={this.props.ndi}
            times={this.props.times[this.props.layerName]}
            layerName={this.props.layerName}
            rootUrl={this.props.rootUrl}
            layers={this.props.catalog.layers}
            activeLayerId={this.props.map.activeLayerId}
            url={this.props.map.url}
            bounds={this.props.map.bounds}
            maxState={this.props.map.maxState}
            maxAverageState={this.props.map.maxAverageState}
            stateAverage={this.props.map.stateAverage}
            stateDiffAverage={this.props.map.stateDiffAverage}
            fetchPolygonalSummary={this.props.actions.fetchPolygonalSummary}
            fetchTimeSeries={this.props.actions.fetchTimeSeries}
            setAnalysisLayer={this.props.actions.setAnalysisLayer} />
        </div>

        <div className="col-md-3" >
          <div style={{"paddingRight": "10px", "paddingTop": "10px"}}>
            <Catalog
              defaultUrl={this.props.rootUrl}
              bounds={this.props.map.bounds}
              onSubmit={url => this.props.actions.fetchCatalog(url)} />
            <Panels
              analysisLayer={this.props.analysisLayer}
              ndi={this.props.ndi}
              rootUrl={this.props.rootUrl}
              layers={this.props.catalog.layers}
              activeLayerId={this.props.map.activeLayerId}
              showExtent={this.props.actions.showExtent}
              showLayer={this.props.actions.showLayer}
              showLayerWithBreaks={this.props.actions.showLayerWithBreaks}
              showMaxState={this.props.actions.showMaxState}
              hideMaxState={this.props.actions.hideMaxState}
              showMaxAverageState={this.props.actions.showMaxAverageState}
              hideMaxAverageState={this.props.actions.hideMaxAverageState}
              showStateAverage={this.props.actions.showStateAverage}
              showStateDiffAverage={this.props.actions.showStateDiffAverage}
              setLayerName={this.props.actions.setLayerName}
              registerTime={this.props.actions.registerTime}
              setIndexType={this.props.actions.setIndexType}
              setLayerType={this.props.actions.setLayerType}
              fetchPolygonalSummary={this.props.actions.fetchPolygonalSummary}
              fetchTimeSeries={this.props.actions.fetchTimeSeries}
            />
          </div>
        </div>
      </div>
    );
  }
});

var mapStateToProps = function (state) {
  return state;
};

var mapDispatchToProps = function (dispatch) {
  return { // binding actions triggers dispatch on call
    actions: bindActionCreators(actions, dispatch)
  };
};

module.exports = connect(mapStateToProps, mapDispatchToProps)(App);
