import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { CompanyApiService } from '../../core/company-api.service';
import { Project, Task } from '../../core/models';
@Component({standalone:true,imports:[CommonModule,RouterLink],templateUrl:'./project-list.component.html',styleUrl:'./project-list.component.scss'})
export class ProjectListComponent implements OnInit {projects=signal<Project[]>([]);tasks=signal<Task[]>([]);selected=signal<Project|null>(null);constructor(public auth:AuthService,private api:CompanyApiService){}ngOnInit(){this.api.projects().subscribe(x=>this.projects.set(x));}select(p:Project){this.selected.set(p);this.api.tasksByProject(p.id).subscribe(x=>this.tasks.set(x));}}
