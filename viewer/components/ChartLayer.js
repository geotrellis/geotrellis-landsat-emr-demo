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
